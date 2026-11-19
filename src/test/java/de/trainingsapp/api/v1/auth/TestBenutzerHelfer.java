package de.trainingsapp.api.v1.auth;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.trainingsapp.domain.benutzer.BenutzerId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;

/**
 * Gemeinsamer Testhelfer zur echten Passkey-Registrierung eines Benutzers in Integrationstests.
 */
public final class TestBenutzerHelfer {

    private static final String ORIGIN_HTTP = "http://localhost:8081";
    private static final String RP_ID = "localhost";

    private TestBenutzerHelfer() {
    }

    public record RegistrierterTestBenutzer(BenutzerId id, String benutzername, String sessionCookie) {
    }

    public static RegistrierterTestBenutzer registriereBenutzer(String praefix) {
        String benutzername = praefix + "." + UUID.randomUUID().toString().substring(0, 8);
        WebAuthnTestAuthenticator authenticator = WebAuthnTestAuthenticator.erstellen(
                WebAuthnTestAuthenticator.Algorithmus.ES256,
                true
        );

        Response opt = given()
                .contentType(ContentType.JSON)
                .body(Map.of("benutzername", benutzername, "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then()
                .statusCode(200)
                .extract().response();

        Map<String, Object> cred = authenticator.erstelleRegistrierungsCredential(
                opt.path("optionen.challenge"),
                ORIGIN_HTTP,
                RP_ID
        );

        Response reg = given()
                .contentType(ContentType.JSON)
                .cookie("_quarkus_webauthn_challenge", opt.cookie("_quarkus_webauthn_challenge"))
                .body(Map.of("benutzername", benutzername, "credential", cred))
                .when().post("/v1/registrieren")
                .then()
                .statusCode(201)
                .extract().response();

        String cookie = reg.cookie("quarkus-credential");
        assertNotNull(cookie);
        assertFalse(cookie.isBlank());
        BenutzerId id = BenutzerId.ausText(reg.path("id"));
        return new RegistrierterTestBenutzer(id, benutzername, cookie);
    }
}
