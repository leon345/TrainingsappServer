package de.trainingsapp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Systematische %prod-Sicherheitsmatrix für alle /q/*-Pfade, alle geschützten /v1/*-Endpunkte,
 * unbekannte /v1/*-Pfade, Security-Header sowie die Trennung des Management-Ports.
 */
@QuarkusTest
@TestProfile(SicherheitsMatrixProdTest.ProdSicherheitsTestProfile.class)
class SicherheitsMatrixProdTest {

    private static final String BEISPIEL_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111").toString();
    private static final String BEISPIEL_UUID_2 = UUID.fromString("22222222-2222-4222-8222-222222222222").toString();

    @TestHTTPResource(value = "/health/live", management = true)
    URL managementHealthLiveUrl;

    @TestHTTPResource(value = "/health/ready", management = true)
    URL managementHealthReadyUrl;

    @TestHTTPResource("/v1/info")
    URL hauptPortInfoUrl;

    public static class ProdSicherheitsTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.smallrye-openapi.enabled", "false",
                    "quarkus.smallrye-openapi.management.enabled", "true",
                    "quarkus.swagger-ui.enabled", "false",
                    "quarkus.management.enabled", "true",
                    "quarkus.management.test-port", "0"
            );
        }
    }

    static Stream<Arguments> interneQPfade() {
        return Stream.of(
                Arguments.of("GET", "/q/openapi"),
                Arguments.of("GET", "/q/swagger-ui"),
                Arguments.of("GET", "/q/swagger-ui/"),
                Arguments.of("GET", "/q/health"),
                Arguments.of("GET", "/q/health/live"),
                Arguments.of("GET", "/q/health/ready"),
                Arguments.of("GET", "/q/webauthn"),
                Arguments.of("GET", "/q/webauthn/login"),
                Arguments.of("POST", "/q/webauthn/login"),
                Arguments.of("GET", "/q/webauthn/register"),
                Arguments.of("POST", "/q/webauthn/register"),
                Arguments.of("GET", "/q/webauthn/login-options-challenge"),
                Arguments.of("GET", "/q/webauthn/register-options-challenge"),
                Arguments.of("GET", "/q/webauthn/webauthn.js"),
                Arguments.of("GET", "/q/webauthn/logout")
        );
    }

    @ParameterizedTest(name = "{0} {1} -> 404 ohne Body und ohne Set-Cookie")
    @MethodSource("interneQPfade")
    void interneEndpunkteAufHauptportLiefern404OhneBodyUndOhneCookie(String methode, String pfad) {
        RequestSpecification req = given().redirects().follow(false);
        if ("POST".equals(methode)) {
            req = req.contentType(ContentType.JSON).body("{}");
        }

        req.when().request(methode, pfad)
                .then()
                .statusCode(404)
                .header("Location", nullValue())
                .header("Set-Cookie", nullValue())
                .body(emptyOrNullString());
    }

    static Stream<Arguments> geschuetzteApiEndpunkte() {
        return Stream.of(
                Arguments.of("GET", "/v1/info", null),
                Arguments.of("GET", "/v1/ich", null),
                Arguments.of("POST", "/v1/abmelden", null),
                Arguments.of("GET", "/v1/passkeys", null),
                Arguments.of("POST", "/v1/passkeys/optionen", null),
                Arguments.of("POST", "/v1/passkeys", "{}"),
                Arguments.of("PATCH", "/v1/passkeys/" + BEISPIEL_UUID, "{\"bezeichnung\":\"Test\"}"),
                Arguments.of("DELETE", "/v1/passkeys/" + BEISPIEL_UUID, null),
                Arguments.of("GET", "/v1/timer/einstellung", null),
                Arguments.of("PUT", "/v1/timer/einstellung", "{\"trainingSekunden\":30,\"pauseSekunden\":15}"),
                Arguments.of("GET", "/v1/workouts", null),
                Arguments.of("POST", "/v1/workouts", "{}"),
                Arguments.of("GET", "/v1/workouts/geteilt", null),
                Arguments.of("GET", "/v1/workouts/" + BEISPIEL_UUID, null),
                Arguments.of("PUT", "/v1/workouts/" + BEISPIEL_UUID, "{}"),
                Arguments.of("DELETE", "/v1/workouts/" + BEISPIEL_UUID, null),
                Arguments.of("PATCH", "/v1/workouts/" + BEISPIEL_UUID + "/sichtbarkeit", "{\"sichtbarkeit\":\"PRIVAT\"}"),
                Arguments.of("GET", "/v1/workouts/" + BEISPIEL_UUID + "/einladungen", null),
                Arguments.of("POST", "/v1/workouts/" + BEISPIEL_UUID + "/einladungen", "{\"benutzername\":\"test\"}"),
                Arguments.of("DELETE", "/v1/workouts/" + BEISPIEL_UUID + "/einladungen/" + BEISPIEL_UUID_2, null),
                Arguments.of("GET", "/v1/benutzer/suche?name=test", null),
                Arguments.of("GET", "/v1/profile/" + BEISPIEL_UUID + "/workouts", null),
                Arguments.of("GET", "/v1/dsgvo/export", null),
                Arguments.of("DELETE", "/v1/konto", null),
                Arguments.of("GET", "/v1/unbekannt", null),
                Arguments.of("POST", "/v1/unbekannt", "{}")
        );
    }

    @ParameterizedTest(name = "{0} {1} -> 401 FehlerV1-JSON + Security-Header")
    @MethodSource("geschuetzteApiEndpunkte")
    void geschuetzteUndUnbekannteApiPfadeLiefernOhneSitzung401MitSicherheitsHeadern(
            String methode,
            String pfad,
            String body
    ) {
        RequestSpecification req = given().redirects().follow(false);
        if (body != null) {
            req = req.contentType(ContentType.JSON).body(body);
        }

        req.when().request(methode, pfad)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("Location", nullValue())
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Content-Security-Policy", equalTo("default-src 'self'"))
                .body("code", equalTo("NICHT_ANGEMELDET"))
                .body("meldung", notNullValue());
    }

    @Test
    void healthEndpunkteSindAufEigenemManagementPortErreichbar() {
        assertTrue(managementHealthLiveUrl.getPort() > 0, "Management-Port muss aktiv sein");
        assertNotEquals(
                hauptPortInfoUrl.getPort(),
                managementHealthLiveUrl.getPort(),
                "Management-Port muss vom öffentlichen API-Port getrennt sein"
        );

        given()
                .when().get(managementHealthLiveUrl)
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        given()
                .when().get(managementHealthReadyUrl)
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
