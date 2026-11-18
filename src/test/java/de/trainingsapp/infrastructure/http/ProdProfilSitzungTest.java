package de.trainingsapp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** B4/E14 im Prod-Profil: Schluessel aus der (simulierten) Umgebungsvariable, Cookies mit Secure ohne Proxy-Header. */
@QuarkusTest
@TestProfile(ProdProfil.class)
@DatenbankErforderlich
class ProdProfilSitzungTest {

    @Test
    void schluesselKommtAusDerUmgebungsvariable() {
        assertEquals("test-schluessel-nur-fuer-tests-0123456789abcdef", ConfigProvider.getConfig()
                .getValue("quarkus.http.auth.session.encryption-key", String.class));
    }

    @Test
    void cookiesSindInProdImmerSecure() {
        Response antwort = given().header("Origin", "http://localhost:8081").post("/v1/anmelden/optionen");
        assertEquals(200, antwort.statusCode());
        CookieHaertungTest.pruefeGenauEinCookie(antwort, CookieHaertungTest.CHALLENGE, true);
    }
}
