package de.trainingsapp.api.v1.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SystemInfoResourceTest {

    @Test
    void getSystemInfoLiefert200JsonUndAlleSicherheitsHeader() {
        given()
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
