package de.trainingsapp.api.v1.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RateLimitIntegrationTest.RateLimitTestProfile.class)
class RateLimitIntegrationTest {

    public static class RateLimitTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "de.trainingsapp.api.v1.auth.AuthResource/anmeldenOptionen/RateLimit/value", "2",
                    "de.trainingsapp.api.v1.auth.AuthResource/registrierenOptionen/RateLimit/value", "2"
            );
        }
    }

    @Test
    void ueberschreitenDesRateLimitsLiefert429AlsFehlerV1() {
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/v1/anmelden/optionen")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body("{}")
                .when().post("/v1/anmelden/optionen")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body("{}")
                .when().post("/v1/anmelden/optionen")
                .then()
                .statusCode(429)
                .contentType(containsString("application/json"))
                .body("code", equalTo("ZU_VIELE_ANFRAGEN"));

        String basis = "rl." + UUID.randomUUID().toString().substring(0, 8);
        given().contentType(ContentType.JSON)
                .body(Map.of("benutzername", basis + "1", "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("benutzername", basis + "2", "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("benutzername", basis + "3", "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then()
                .statusCode(429)
                .contentType(containsString("application/json"))
                .body("code", equalTo("ZU_VIELE_ANFRAGEN"));
    }
}
