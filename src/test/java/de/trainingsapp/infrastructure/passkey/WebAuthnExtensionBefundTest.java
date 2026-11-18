package de.trainingsapp.infrastructure.passkey;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Belege fuer docs/spike-s04-passkeys.md: Verhalten der Quarkus-WebAuthn-Extension 3.40
 * ohne weitere Konfiguration. Schlaegt einer dieser Tests nach einem Quarkus-Update fehl,
 * hat sich das Verhalten geaendert und der Befund ist neu zu bewerten (E13).
 * Der Befund zum Sitzungs-Cookie steht in {@link WebAuthnSitzungsCookieBefundTest}.
 *
 * <p>Seit S-04 mit Datenbank: Der eingebaute Endpunkt register-options-challenge fragt den
 * {@code WebAuthnUserProvider} und damit die Tabelle {@code benutzer}.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class WebAuthnExtensionBefundTest {

    @Test
    void eingebauteChallengeEndpunkteUnterQSindImmerAktiv() {
        // nicht einzeln abschaltbar (nur die ganze Extension per quarkus.webauthn.enabled=false)
        given().queryParam("username", "x").get("/q/webauthn/register-options-challenge")
                .then().statusCode(200).cookie("_quarkus_webauthn_challenge", notNullValue());
        given().get("/q/webauthn/login-options-challenge").then().statusCode(200);
        given().get("/q/webauthn/webauthn.js").then().statusCode(200);
        given().redirects().follow(false).get("/q/webauthn/logout").then().statusCode(302);
        given().get("/.well-known/webauthn").then().statusCode(200);
    }

    @Test
    void eingebauteRegistrierungUndAnmeldungSindAbgeschaltet() {
        given().contentType(ContentType.JSON).body("{}").post("/q/webauthn/register").then().statusCode(404);
        given().contentType(ContentType.JSON).body("{}").post("/q/webauthn/login").then().statusCode(404);
    }
}
