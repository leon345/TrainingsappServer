package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OpenApiTest {

    @Test
    void openApiEnthaeltAlleV1EndpunkteUndSchemas() {
        given()
                .accept("application/json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths.'/v1/info'.get.operationId", equalTo("getSystemInfoV1"))
                .body("paths.'/v1/registrieren/optionen'.post.operationId", equalTo("registrierenOptionenV1"))
                .body("paths.'/v1/registrieren'.post.operationId", equalTo("registrierenV1"))
                .body("paths.'/v1/anmelden/optionen'.post.operationId", equalTo("anmeldenOptionenV1"))
                .body("paths.'/v1/anmelden'.post.operationId", equalTo("anmeldenV1"))
                .body("paths.'/v1/abmelden'.post.operationId", equalTo("abmeldenV1"))
                .body("paths.'/v1/ich'.get.operationId", equalTo("ichV1"))
                .body("paths.'/v1/passkeys'.get.operationId", equalTo("listePasskeysV1"))
                .body("paths.'/v1/passkeys/optionen'.post.operationId", equalTo("passkeyOptionenV1"))
                .body("paths.'/v1/passkeys'.post.operationId", equalTo("passkeyHinzufuegenV1"))
                .body("paths.'/v1/passkeys/{id}'.patch.operationId", equalTo("passkeyUmbenennenV1"))
                .body("paths.'/v1/passkeys/{id}'.delete.operationId", equalTo("passkeyLoeschenV1"))
                .body("components.schemas.SystemInfoV1", notNullValue())
                .body("components.schemas.FehlerV1", notNullValue())
                .body("components.schemas.BenutzerV1", notNullValue())
                .body("components.schemas.PasskeyV1", notNullValue())
                .body("components.schemas.WebAuthnOptionenV1", notNullValue());
    }
}
