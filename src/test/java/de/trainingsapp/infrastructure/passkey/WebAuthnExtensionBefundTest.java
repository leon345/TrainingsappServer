package de.trainingsapp.infrastructure.passkey;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.webauthn.WebAuthnHardware;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URL;

/**
 * Belege fuer docs/spike-s04-passkeys.md: Verhalten der Quarkus-WebAuthn-Extension 3.40
 * ohne weitere Konfiguration. Schlaegt einer dieser Tests nach einem Quarkus-Update fehl,
 * hat sich das Verhalten geaendert und der Befund ist neu zu bewerten.
 */
@QuarkusTest
class WebAuthnExtensionBefundTest {

    @TestHTTPResource
    URL url;

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

    @Test
    void sitzungsCookieIstSameSiteStrictAberNichtHttpOnly() {
        WebAuthnHardware hw = new WebAuthnHardware(url);
        CookieFilter browser = new CookieFilter();
        String ch = new JsonObject(given().filter(browser).contentType(ContentType.JSON)
                .body("{\"benutzername\":\"cookie-test\"}").post("/v1/registrieren/optionen")
                .then().statusCode(200).extract().asString()).getString("challenge");
        given().filter(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", "cookie-test")
                        .put("credential", hw.makeRegistrationJson(ch)).encode())
                .post("/v1/registrieren")
                .then().statusCode(204)
                .header("Set-Cookie", containsString("quarkus-credential="))
                .header("Set-Cookie", containsString("SameSite=Strict"))
                // WebAuthnRecorder uebergibt httpOnly=false fest an den PersistentLoginManager
                .header("Set-Cookie", not(containsString("HttpOnly")));
    }
}
