package de.trainingsapp.infrastructure.passkey;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Beleg: /q/webauthn/* laesst sich per HTTP-Permission-Policy sperren, ohne die
 * /v1-Endpunkte zu beeintraechtigen. Die Sperre antwortet allerdings mit 302 auf
 * quarkus.webauthn.login-page (Challenge des WebAuthn-Mechanismus), nicht mit 401/403/404.
 */
@QuarkusTest
@TestProfile(WebAuthnQPfadeGesperrtTest.QGesperrt.class)
class WebAuthnQPfadeGesperrtTest {

    public static class QGesperrt implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.auth.permission.webauthn-q.paths", "/q/webauthn/*",
                    "quarkus.http.auth.permission.webauthn-q.policy", "deny");
        }
    }

    @Test
    void qWebauthnIstGesperrt() {
        for (String pfad : new String[] {"/q/webauthn/register-options-challenge?username=x",
                "/q/webauthn/login-options-challenge", "/q/webauthn/webauthn.js", "/q/webauthn/logout"}) {
            given().redirects().follow(false).get(pfad).then().statusCode(302);
        }
    }

    @Test
    void v1AnmeldungBleibtErreichbar() {
        given().post("/v1/anmelden/optionen").then().statusCode(200);
    }
}
