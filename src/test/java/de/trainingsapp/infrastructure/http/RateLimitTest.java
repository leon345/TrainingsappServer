package de.trainingsapp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import de.trainingsapp.testhilfe.StellbareUhr;
import de.trainingsapp.testhilfe.TestOrigin;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * E17: Rate-Limit je Client-IP mit kontrollierter Uhr. Grenze im Profil: 3 Anfragen je 10 Minuten.
 * Hier ist 127.0.0.1 (der Testclient) als Proxy vertrauenswuerdig (wie der Reverse Proxy in Prod),
 * daher bestimmt X-Forwarded-For die Client-IP. Den direkt erreichbaren Fall prueft
 * {@link RateLimitOhneVertrauenswuerdigenProxyTest}.
 */
@QuarkusTest
@TestProfile(RateLimitTest.Profil.class)
class RateLimitTest {

    public static class Profil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("trainingsapp.rate-limit.anfragen", "3",
                    "trainingsapp.rate-limit.fenster", "PT10M",
                    "quarkus.http.proxy.trusted-proxies", "127.0.0.1");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(StellbareUhr.class);
        }
    }

    @Inject
    StellbareUhr uhr;

    @BeforeEach
    void uhrZuruecksetzen() {
        uhr.zuruecksetzen();
    }

    @Test
    void ueberschreitungGibt429MitRetryAfterBisZumFensterende() {
        String ip = "203.0.113.1";
        for (int i = 0; i < 3; i++) {
            anmeldeOptionen(ip).statusCode(200);
        }
        anmeldeOptionen(ip).statusCode(429)
                .contentType("application/json")
                .header("Retry-After", "600")
                .body("code", equalTo("ZU_VIELE_ANFRAGEN"));

        uhr.vor(Duration.ofSeconds(599).plusMillis(500));
        anmeldeOptionen(ip).statusCode(429).header("Retry-After", "1");

        uhr.vor(Duration.ofMillis(500));
        anmeldeOptionen(ip).statusCode(200);
    }

    @Test
    void jeEndpunktEinEigenesKontingentUndVorDerWebAuthnPruefung() {
        String ip = "203.0.113.2";
        for (int i = 0; i < 3; i++) {
            anmeldeOptionen(ip).statusCode(200);
        }
        anmeldeOptionen(ip).statusCode(429);

        // registrieren/optionen und registrieren zaehlen getrennt; die Anfragen sind ungueltig (400),
        // werden aber gezaehlt, weil das Limit vor Validierung und WebAuthn-Pruefung greift.
        for (int i = 0; i < 3; i++) {
            post(ip, "/v1/registrieren/optionen", "{\"benutzername\":\"a@b\"}").statusCode(400);
            post(ip, "/v1/registrieren", "{}").statusCode(400);
        }
        post(ip, "/v1/registrieren/optionen", "{\"benutzername\":\"a@b\"}").statusCode(429);
        post(ip, "/v1/registrieren", "{}").statusCode(429);
    }

    @Test
    void andereClientIpHatEigenesKontingent() {
        for (int i = 0; i < 3; i++) {
            anmeldeOptionen("203.0.113.3").statusCode(200);
        }
        anmeldeOptionen("203.0.113.3").statusCode(429);
        anmeldeOptionen("203.0.113.4").statusCode(200);
    }

    /**
     * Quarkus nimmt den ERSTEN Eintrag aus X-Forwarded-For. Haengt der Proxy die echte Adresse nur an,
     * bestimmt der Client den gezaehlten Eintrag selbst. Der Proxy muss den Header daher ueberschreiben.
     */
    @Test
    void ersterEintragVonXForwardedForZaehlt() {
        for (int i = 0; i < 3; i++) {
            anmeldeOptionen("203.0.113.5, 198.51.100.7").statusCode(200);
        }
        anmeldeOptionen("203.0.113.5").statusCode(429);
        anmeldeOptionen("198.51.100.7").statusCode(200);
    }

    @Test
    void anderePfadeUndGetSindNichtBegrenzt() {
        String ip = "203.0.113.6";
        for (int i = 0; i < 5; i++) {
            given().header("X-Forwarded-For", ip).get("/v1/anmelden/optionen").then().statusCode(405);
            post(ip, "/v1/anmelden", "{}").statusCode(400);
        }
    }

    private static ValidatableResponse anmeldeOptionen(String xForwardedFor) {
        return post(xForwardedFor, "/v1/anmelden/optionen", null);
    }

    private static ValidatableResponse post(String xForwardedFor, String pfad, String body) {
        var anfrage = given().header("Origin", TestOrigin.wert()).header("X-Forwarded-For", xForwardedFor);
        if (body != null) {
            anfrage.contentType(ContentType.JSON).body(body);
        }
        return anfrage.post(pfad).then();
    }
}
