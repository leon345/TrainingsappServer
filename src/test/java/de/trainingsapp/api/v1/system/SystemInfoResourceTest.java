package de.trainingsapp.api.v1.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import de.trainingsapp.api.v1.auth.WebAuthnTestAuthenticator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SystemInfoResourceTest {

    @Test
    void getSystemInfoOhneAnmeldungLiefert401JsonUndAlleSicherheitsHeader() {
        given()
                .redirects().follow(false)
                .when().get("/v1/info")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Content-Security-Policy", equalTo("default-src 'self'"))
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }

    @Test
    void getSystemInfoMitAnmeldungLiefert200JsonUndAlleSicherheitsHeader() {
        String name = "sys." + UUID.randomUUID().toString().substring(0, 8);
        WebAuthnTestAuthenticator auth = WebAuthnTestAuthenticator.erstellen(
                WebAuthnTestAuthenticator.Algorithmus.ES256,
                true
        );
        Response opt = given()
                .contentType(ContentType.JSON)
                .body(Map.of("benutzername", name, "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then().statusCode(200).extract().response();

        Response reg = given()
                .contentType(ContentType.JSON)
                .cookie("_quarkus_webauthn_challenge", opt.cookie("_quarkus_webauthn_challenge"))
                .body(Map.of(
                        "benutzername", name,
                        "credential", auth.erstelleRegistrierungsCredential(
                                opt.path("optionen.challenge"),
                                "http://localhost:8081",
                                "localhost"
                        )
                ))
                .when().post("/v1/registrieren")
                .then().statusCode(201).extract().response();

        given()
                .cookie("quarkus-credential", reg.cookie("quarkus-credential"))
                .when().get("/v1/info")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Content-Security-Policy", equalTo("default-src 'self'"))
                .body("name", equalTo("trainingsapp-server"))
                .body("version", equalTo("0.1.0-SNAPSHOT"));
    }

    @Test
    void validierungsfehlerLiefert400AlsFehlerV1() {
        given()
                .queryParam("dauerSekunden", 0)
                .when().get("/v1/test-fehler/validierung")
                .then()
                .statusCode(400)
                .contentType(containsString("application/json"))
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"))
                .body("meldung", containsString("dauerSekunden muss mindestens 1 sein"));
    }

    @Test
    void domainFehlerLiefert400AlsFehlerV1() {
        given()
                .when().get("/v1/test-fehler/domain")
                .then()
                .statusCode(400)
                .contentType(containsString("application/json"))
                .body("code", equalTo("FACHLICHER_FEHLER"))
                .body("meldung", equalTo("Fachliche Regel verletzt."));
    }
}
