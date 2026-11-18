package de.trainingsapp.infrastructure.passkey;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.webauthn.WebAuthnHardware;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URL;

/**
 * Befund (docs/spike-s04-passkeys.md, Abschnitt 5): Attribute des Sitzungs-Cookies der
 * Quarkus-WebAuthn-Extension 3.40 ohne weitere Konfiguration, ueber die eigenen /v1-Endpunkte.
 * Aus {@link WebAuthnExtensionBefundTest} herausgeloest, Inhalt unveraendert. Seit E14 setzt
 * {@code infrastructure.http.CookieHaertung} HttpOnly; das Profil schliesst sie aus, damit der Befund
 * ueber die Extension pruefbar bleibt (E13). Schlaegt der Test
 * nach einem Quarkus-Update fehl, ist der Befund neu zu bewerten (E13).
 */
@QuarkusTest
@TestProfile(WebAuthnSitzungsCookieBefundTest.OhneCookieHaertung.class)
@DatenbankErforderlich
class WebAuthnSitzungsCookieBefundTest {

    /** Befund ueber die Extension selbst: ohne unsere Cookie-Haertung (E14), sonst wie MitDatenbank. */
    public static class OhneCookieHaertung extends MitDatenbank {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            java.util.Map<String, String> config = new java.util.HashMap<>(super.getConfigOverrides());
            config.put("quarkus.arc.exclude-types", "de.trainingsapp.infrastructure.http.CookieHaertung");
            return config;
        }
    }

    @TestHTTPResource
    URL url;

    @Test
    void sitzungsCookieIstSameSiteStrictAberNichtHttpOnly() {
        WebAuthnHardware hw = new WebAuthnHardware(url);
        CookieFilter browser = new CookieFilter();
        String ch = new JsonObject(given().filter(browser).header("Origin", de.trainingsapp.testhilfe.TestOrigin.wert()).contentType(ContentType.JSON)
                .body("{\"benutzername\":\"cookie-test\"}").post("/v1/registrieren/optionen")
                .then().statusCode(200).extract().asString()).getString("challenge");
        given().filter(browser).header("Origin", de.trainingsapp.testhilfe.TestOrigin.wert()).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", "cookie-test")
                        .put("credential", hw.makeRegistrationJson(ch)).encode())
                .post("/v1/registrieren")
                .then().statusCode(204)
                .header("Set-Cookie", containsString("quarkus-credential="))
                .header("Set-Cookie", containsString("SameSite=Strict"))
                // WebAuthnRecorder uebergibt httpOnly=false fest an den PersistentLoginManager
                .header("Set-Cookie", not(containsString("HttpOnly")));
    }
}
