package de.trainingsapp.infrastructure.http;

import static de.trainingsapp.testhilfe.Ablauf.im;
import static de.trainingsapp.testhilfe.Ablauf.registrieren;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import de.trainingsapp.testhilfe.TestAuthenticator;
import de.trainingsapp.testhilfe.TestOrigin;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** E15: Origin-Pruefung gegen die Allowlist fuer alle schreibenden Requests unter /v1. */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class OriginPruefungTest {

    @TestHTTPResource
    URL url;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST, /v1/registrieren/optionen",
            "POST, /v1/registrieren",
            "POST, /v1/anmelden/optionen",
            "POST, /v1/anmelden",
            "POST, /v1/abmelden",
            "POST, /v1/passkeys/optionen",
            "POST, /v1/passkeys",
            "PATCH, /v1/passkeys/abc",
            "DELETE, /v1/passkeys/abc",
            "PUT, /v1/timer/einstellung",
            "POST, /v1/gibt-es-nicht"})
    void fremdeOderFehlendeOriginWirdAbgewiesen(String methode, String pfad) {
        for (String origin : new String[] {"https://evil.example", "http://localhost:8080", "null",
                TestOrigin.wert() + "/", TestOrigin.wert().toUpperCase(), null}) {
            RequestSpecification anfrage = given().redirects().follow(false).contentType(ContentType.JSON).body("{}");
            if (origin != null) {
                anfrage.header("Origin", origin);
            }
            anfrage.request(methode, pfad).then()
                    .statusCode(403)
                    .contentType("application/json")
                    .body("code", equalTo("ORIGIN_UNGUELTIG"));
        }
    }

    @Test
    void abgewieseneAnfrageVeraendertNichts() {
        CookieFilter browser = registrieren("origin-opfer", new TestAuthenticator(url));
        String id = im(browser).get("/v1/passkeys").then().extract().path("[0].id");
        hinzufuegenVonAussen(browser);

        // Mit gueltiger Sitzung, aber fremder Origin: weder Loeschen noch Umbenennen noch Abmelden
        given().filter(browser).header("Origin", "https://evil.example").contentType(ContentType.JSON)
                .body("{\"bezeichnung\":\"gekapert\"}").patch("/v1/passkeys/" + id).then().statusCode(403);
        given().filter(browser).header("Origin", "https://evil.example").post("/v1/abmelden").then().statusCode(403);
        Response liste = im(browser).get("/v1/passkeys");
        liste.then().statusCode(200).body("$", hasSize(2));
        assertTrue(liste.jsonPath().getList("bezeichnung").stream().noneMatch("gekapert"::equals));
    }

    @Test
    void erlaubteOriginGehtDurch() {
        CookieFilter browser = registrieren("origin-erlaubt", new TestAuthenticator(url));
        im(browser).post("/v1/abmelden").then().statusCode(204);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/info", "/v1/passkeys"})
    void lesendeAnfragenBrauchenKeineOrigin(String pfad) {
        given().get(pfad).then().statusCode(401); // Pfad-Policy, nicht Origin-Pruefung
    }

    private void hinzufuegenVonAussen(CookieFilter browser) {
        de.trainingsapp.testhilfe.Ablauf.hinzufuegen(browser, new TestAuthenticator(url));
    }
}
