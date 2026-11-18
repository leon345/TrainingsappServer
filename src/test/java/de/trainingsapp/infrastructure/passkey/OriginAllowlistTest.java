package de.trainingsapp.infrastructure.passkey;

import static de.trainingsapp.testhilfe.Ablauf.registrierungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.registrierungsOptionen;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import de.trainingsapp.testhilfe.TestAuthenticator;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * E23, Abnahme 7: Origins und rpId kommen nur aus der Konfiguration (Allowlist) und sind im Test
 * ueberschreibbar. Hier ist die Allowlist auf https://app.example.de umgestellt: Der Server laeuft
 * weiter auf localhost, aber nur Antworten fuer die konfigurierte Origin und rpId werden akzeptiert.
 */
@QuarkusTest
@TestProfile(OriginAllowlistTest.FremdeOrigin.class)
@DatenbankErforderlich
class OriginAllowlistTest {

    public static class FremdeOrigin extends MitDatenbank {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>(super.getConfigOverrides());
            config.put("quarkus.webauthn.origins", "https://app.example.de");
            config.put("quarkus.webauthn.relying-party.id", "app.example.de");
            return config;
        }
    }

    @TestHTTPResource
    URL url;

    @Test
    void optionenTragenDieKonfigurierteRpId() {
        JsonObject optionen = registrierungsOptionen(new CookieFilter(), "allow-eins");
        assertEquals("app.example.de", optionen.getJsonObject("rp").getString("id"));
    }

    @Test
    void antwortFuerDieKonfigurierteOriginWirdAngenommen() {
        CookieFilter browser = new CookieFilter();
        JsonObject optionen = registrierungsOptionen(browser, "allow-zwei");
        registrierungAbschliessen(browser, "allow-zwei", new TestAuthenticator(url).registriere(optionen))
                .then().statusCode(204);
    }

    @Test
    void antwortFuerDenTatsaechlichenHostWirdAbgelehnt() {
        // Ohne Allowlist haette die Extension den Origin aus der Request-URL (localhost) abgeleitet.
        CookieFilter browser = new CookieFilter();
        JsonObject optionen = registrierungsOptionen(browser, "allow-drei");
        TestAuthenticator lokal = new TestAuthenticator(url).mitOrigin("http://localhost:" + url.getPort())
                .mitRpId("localhost");
        registrierungAbschliessen(browser, "allow-drei", lokal.registriere(optionen)).then().statusCode(400);
    }
}
