package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SystemInfoResourceTest {

    @Test
    void infoEndpointIstUnterV1Erreichbar() {
        given()
                .when().get("/v1/info")
                .then()
                .statusCode(200)
                .body("name", equalTo("trainingsapp-server"))
                .body("version", notNullValue());
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
