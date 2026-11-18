package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SystemInfoResourceTest {

    @Test
    @TestSecurity(user = "00000000-0000-4000-8000-000000000001")
    void infoEndpointIstUnterV1Erreichbar() {
        given()
                .when().get("/v1/info")
                .then()
                .statusCode(200)
                .body("name", equalTo("trainingsapp-server"))
                .body("version", notNullValue());
    }

    /** E25: ohne Anmeldung 401 als JSON, nie 302. */
    @Test
    void infoEndpointOhneAnmeldungIst401AlsJson() {
        given().redirects().follow(false)
                .when().get("/v1/info")
                .then()
                .statusCode(401)
                .contentType("application/json")
                .header("Location", nullValue())
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }

    @Test
    void openApiDokumentEnthaeltV1Endpunkt() {
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths", org.hamcrest.Matchers.hasKey("/v1/info"))
                .body("info.title", equalTo("Trainingsapp API"));
    }

    @Test
    void openApiDokumentAlsYamlAbrufbar() {
        given()
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body(containsString("/v1/info"));
    }
}
