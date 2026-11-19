package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OpenApiTest {

    @Test
    void openApiEnthaeltSystemInfoEndpunktUndSchemas() {
        given()
                .accept("application/json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths.'/v1/info'.get.operationId", equalTo("getSystemInfoV1"))
                .body("components.schemas.SystemInfoV1", notNullValue())
                .body("components.schemas.SystemInfoV1.properties.name", notNullValue())
                .body("components.schemas.SystemInfoV1.properties.version", notNullValue())
                .body("components.schemas.FehlerV1", notNullValue())
                .body("components.schemas.FehlerV1.properties.code", notNullValue())
                .body("components.schemas.FehlerV1.properties.meldung", notNullValue());
    }
}
