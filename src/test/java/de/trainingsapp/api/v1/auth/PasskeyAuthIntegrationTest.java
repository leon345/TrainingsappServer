package de.trainingsapp.api.v1.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.webauthn4j.util.Base64UrlUtil;
import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PasskeyAuthIntegrationTest {

    private static final String ORIGIN_HTTP = "http://localhost:8081";
    private static final String ORIGIN_HTTPS = "https://localhost:8081";
    private static final String RP_ID = "localhost";

    @Test
    void vollständigerPasskeyLebenszyklusMitEs256Rs256SignCountNullUndMultiPasskey() {
        String benutzername = "athlet." + UUID.randomUUID().toString().substring(0, 8);
        WebAuthnTestAuthenticator es256Null = WebAuthnTestAuthenticator.erstellen(
                WebAuthnTestAuthenticator.Algorithmus.ES256,
                true
        );
        WebAuthnTestAuthenticator rs256 = WebAuthnTestAuthenticator.erstellen(
                WebAuthnTestAuthenticator.Algorithmus.RS256,
                false
        );
        WebAuthnTestAuthenticator dritterAuth = WebAuthnTestAuthenticator.erstellen(
                WebAuthnTestAuthenticator.Algorithmus.ES256,
                false
        );

        // 1. Ohne Einwilligung -> 400 EINWILLIGUNG_ERFORDERLICH
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("benutzername", benutzername, "einwilligungErteilt", false))
                .when().post("/v1/registrieren/optionen")
                .then()
                .statusCode(400)
                .body("code", equalTo("EINWILLIGUNG_ERFORDERLICH"));

        // 2. Registrierungsoptionen anfordern (inkl. X-Forwarded-Proto: https -> Secure + SameSite=Strict!)
        Response optionenResponse = given()
                .contentType(ContentType.JSON)
                .header("X-Forwarded-Proto", "https")
                .body(Map.of("benutzername", benutzername.toUpperCase(), "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then()
                .statusCode(200)
                .body("optionen.rp.id", equalTo(RP_ID))
                .body("optionen.user.name", equalTo(benutzername))
                .body("optionen.pubKeyCredParams.alg", hasItem(-7))
                .body("optionen.pubKeyCredParams.alg", hasItem(-257))
                .body("optionen.pubKeyCredParams.alg", not(hasItem(-8)))
                .extract().response();

        List<String> optionenCookies = optionenResponse.headers().getValues("Set-Cookie");
        assertTrue(
                optionenCookies.stream().anyMatch(c ->
                        c.contains("_quarkus_webauthn_challenge=")
                                && c.contains("SameSite=Strict")
                                && c.contains("Secure")),
                "Challenge-Cookie muss SameSite=Strict und Secure hinter X-Forwarded-Proto: https tragen: " + optionenCookies
        );

        String challenge = optionenResponse.path("optionen.challenge");
        String userHandleBase64Url = optionenResponse.path("optionen.user.id");
        BenutzerId erwarteteBenutzerId = BenutzerId.ausUserHandle(Base64UrlUtil.decode(userHandleBase64Url));
        String challengeCookie = optionenResponse.cookie("_quarkus_webauthn_challenge");
        assertNotNull(challengeCookie);

        // 3. Registrierung mit ES256 (SignCount 0) abschließen -> 201 + Session-Cookie
        Map<String, Object> regCred = es256Null.erstelleRegistrierungsCredential(challenge, ORIGIN_HTTPS, RP_ID);
        Response regResponse = given()
                .contentType(ContentType.JSON)
                .header("X-Forwarded-Proto", "https")
                .cookie("_quarkus_webauthn_challenge", challengeCookie)
                .body(Map.of("benutzername", benutzername, "credential", regCred))
                .when().post("/v1/registrieren")
                .then()
                .statusCode(201)
                .body("id", equalTo(erwarteteBenutzerId.alsText()))
                .body("benutzername", equalTo(benutzername))
                .body("rolle", equalTo("USER"))
                .body("erstelltAm", notNullValue())
                .body("einwilligungAm", notNullValue())
                .extract().response();

        List<String> regCookies = regResponse.headers().getValues("Set-Cookie");
        assertTrue(
                regCookies.stream().anyMatch(c ->
                        c.contains("quarkus-credential=")
                                && c.contains("SameSite=Strict")
                                && c.contains("Secure")),
                "Session-Cookie muss SameSite=Strict und Secure hinter X-Forwarded-Proto: https tragen: " + regCookies
        );

        String sessionCookie = regResponse.cookie("quarkus-credential");
        assertNotNull(sessionCookie);

        // 4. Angemeldet: /v1/ich und /v1/info liefern 200
        given()
                .cookie("quarkus-credential", sessionCookie)
                .when().get("/v1/ich")
                .then()
                .statusCode(200)
                .body("id", equalTo(erwarteteBenutzerId.alsText()))
                .body("benutzername", equalTo(benutzername));

        given()
                .cookie("quarkus-credential", sessionCookie)
                .when().get("/v1/info")
                .then()
                .statusCode(200)
                .body("name", equalTo("trainingsapp-server"));

        // 5. Passkey-Liste enthält genau "Passkey 1" (erstelltAm heute UTC, zuletztVerwendetAm null)
        String passkey1Id = given()
                .cookie("quarkus-credential", sessionCookie)
                .when().get("/v1/passkeys")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].bezeichnung", equalTo("Passkey 1"))
                .body("[0].erstelltAm", equalTo(LocalDate.now(ZoneOffset.UTC).toString()))
                .body("[0].zuletztVerwendetAm", nullValue())
                .extract().path("[0].id");

        // 6. Letzter Passkey darf nicht gelöscht werden -> 409 LETZTER_PASSKEY
        given()
                .cookie("quarkus-credential", sessionCookie)
                .when().delete("/v1/passkeys/" + passkey1Id)
                .then()
                .statusCode(409)
                .body("code", equalTo("LETZTER_PASSKEY"));

        // 7. Zweiten Passkey mit RS256 hinzufügen (automatisch "Passkey 2", excludeCredentials enthält Passkey 1)
        Response addOptResp = given()
                .cookie("quarkus-credential", sessionCookie)
                .when().post("/v1/passkeys/optionen")
                .then()
                .statusCode(200)
                .body("optionen.excludeCredentials", hasSize(1))
                .body("optionen.excludeCredentials[0].id", equalTo(es256Null.credentialId()))
                .extract().response();

        String addChallenge = addOptResp.path("optionen.challenge");
        String addChallengeCookie = addOptResp.cookie("_quarkus_webauthn_challenge");
        Map<String, Object> rs256Cred = rs256.erstelleRegistrierungsCredential(addChallenge, ORIGIN_HTTP, RP_ID);

        String passkey2Id = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", sessionCookie)
                .cookie("_quarkus_webauthn_challenge", addChallengeCookie)
                .body(Map.of("credential", rs256Cred))
                .when().post("/v1/passkeys")
                .then()
                .statusCode(201)
                .body("bezeichnung", equalTo("Passkey 2"))
                .extract().path("id");

        // 8. Abmelden -> Session ungültig (401 JSON, kein 302)
        Response logoutResp = given()
                .cookie("quarkus-credential", sessionCookie)
                .when().post("/v1/abmelden")
                .then()
                .statusCode(204)
                .extract().response();
        assertTrue(logoutResp.headers().getValues("Set-Cookie").stream().anyMatch(c -> c.contains("quarkus-credential=")));

        // 9. Discoverable Login (ohne Benutzername) mit Passkey 1 (ES256, SignCount 0) -> 200
        String loginSession1 = meldeMitPasskeyAn(es256Null, null, userHandleBase64Url);

        // Zweite Anmeldung mit SignCount 0 muss ebenfalls erfolgreich sein!
        String loginSession1b = meldeMitPasskeyAn(es256Null, null, userHandleBase64Url);

        // 10. Anmeldung mit Benutzername und Passkey 2 (RS256, inkrementierender SignCount) -> 200
        String loginSession2 = meldeMitPasskeyAn(rs256, benutzername, userHandleBase64Url);

        // Beide Passkeys haben nun zuletztVerwendetAm = heute UTC
        given()
                .cookie("quarkus-credential", loginSession2)
                .when().get("/v1/passkeys")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].zuletztVerwendetAm", equalTo(LocalDate.now(ZoneOffset.UTC).toString()))
                .body("[1].zuletztVerwendetAm", equalTo(LocalDate.now(ZoneOffset.UTC).toString()));

        // 11. Passkey 2 umbenennen (Trim + NFC)
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", loginSession1)
                .body(Map.of("bezeichnung", "  Büro Schlüssel  "))
                .when().patch("/v1/passkeys/" + passkey2Id)
                .then()
                .statusCode(200)
                .body("bezeichnung", equalTo("Büro Schlüssel"));

        // 12. Passkey 2 löschen -> 204; danach neuer Passkey erhält automatisch "Passkey 3" (Nummer 2 nicht wiederverwendet!)
        given()
                .cookie("quarkus-credential", loginSession1b)
                .when().delete("/v1/passkeys/" + passkey2Id)
                .then()
                .statusCode(204);

        Response addOpt3 = given()
                .cookie("quarkus-credential", loginSession1b)
                .when().post("/v1/passkeys/optionen")
                .then()
                .statusCode(200)
                .extract().response();

        Map<String, Object> cred3 = dritterAuth.erstelleRegistrierungsCredential(
                addOpt3.path("optionen.challenge"),
                ORIGIN_HTTP,
                RP_ID
        );
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", loginSession1b)
                .cookie("_quarkus_webauthn_challenge", addOpt3.cookie("_quarkus_webauthn_challenge"))
                .body(Map.of("credential", cred3))
                .when().post("/v1/passkeys")
                .then()
                .statusCode(201)
                .body("bezeichnung", equalTo("Passkey 3"));

        // 13. Zweiten Benutzer B registrieren: Zugriff auf Passkey von A -> einheitlich 404 (keine ID-Aufzählung!)
        String benutzerB = "fremd." + UUID.randomUUID().toString().substring(0, 8);
        String sessionB = registriereBenutzer(
                benutzerB,
                WebAuthnTestAuthenticator.erstellen(WebAuthnTestAuthenticator.Algorithmus.ES256, true)
        );

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", sessionB)
                .body(Map.of("bezeichnung", "Gehackt"))
                .when().patch("/v1/passkeys/" + passkey1Id)
                .then()
                .statusCode(404)
                .body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));

        given()
                .cookie("quarkus-credential", sessionB)
                .when().delete("/v1/passkeys/" + passkey1Id)
                .then()
                .statusCode(404)
                .body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));

        // Nicht existierende UUID liefert identisches 404
        given()
                .cookie("quarkus-credential", sessionB)
                .when().delete("/v1/passkeys/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));

        // RBAC: USER darf nicht auf @RolesAllowed("ADMIN") zugreifen -> 403 ZUGRIFF_VERWEIGERT
        given()
                .cookie("quarkus-credential", sessionB)
                .when().get("/v1/test-fehler/nur-admin")
                .then()
                .statusCode(403)
                .contentType(containsString("application/json"))
                .body("code", equalTo("ZUGRIFF_VERWEIGERT"));

        // Doppelter Benutzername bei Registrierung -> 409 BENUTZERNAME_VERGEBEN
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("benutzername", benutzername, "einwilligungErteilt", true))
                .when().post("/v1/registrieren/optionen")
                .then()
                .statusCode(409)
                .body("code", equalTo("BENUTZERNAME_VERGEBEN"));
    }

    @Test
    void unauthentifizierteGeschuetzteEndpunkteLiefern401FehlerV1JsonOhneRedirect() {
        String zufallsUuid = UUID.randomUUID().toString();

        for (String getPfad : List.of("/v1/info", "/v1/ich", "/v1/passkeys")) {
            given()
                    .redirects().follow(false)
                    .when().get(getPfad)
                    .then()
                    .statusCode(401)
                    .contentType(containsString("application/json"))
                    .header("Location", nullValue())
                    .body("code", equalTo("NICHT_ANGEMELDET"))
                    .body("meldung", notNullValue());
        }

        given()
                .redirects().follow(false)
                .when().post("/v1/abmelden")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .contentType(ContentType.JSON)
                .body(Map.of("bezeichnung", "Test"))
                .when().patch("/v1/passkeys/" + zufallsUuid)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .when().delete("/v1/passkeys/" + zufallsUuid)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }

    @Test
    void standardWebAuthnRoutenUnterQWebauthnLiefern404OhneBodyUndOhneCookie() {
        for (String getPfad : List.of(
                "/q/webauthn",
                "/q/webauthn/login-options-challenge",
                "/q/webauthn/register-options-challenge",
                "/q/webauthn/webauthn.js",
                "/q/webauthn/logout"
        )) {
            given()
                    .redirects().follow(false)
                    .when().get(getPfad)
                    .then()
                    .statusCode(404)
                    .body(emptyOrNullString())
                    .header("Set-Cookie", nullValue());
        }

        for (String postPfad : List.of("/q/webauthn/login", "/q/webauthn/register")) {
            given()
                    .redirects().follow(false)
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when().post(postPfad)
                    .then()
                    .statusCode(404)
                    .body(emptyOrNullString())
                    .header("Set-Cookie", nullValue());
        }
    }

    private String registriereBenutzer(String benutzername, WebAuthnTestAuthenticator authenticator) {
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
        return cookie;
    }

    private String meldeMitPasskeyAn(
            WebAuthnTestAuthenticator authenticator,
            String optionalerBenutzername,
            String userHandleBase64Url
    ) {
        Map<String, Object> body = optionalerBenutzername != null
                ? Map.of("benutzername", optionalerBenutzername)
                : Map.of();

        Response opt = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/v1/anmelden/optionen")
                .then()
                .statusCode(200)
                .extract().response();

        if (optionalerBenutzername != null) {
            List<String> erlaubte = opt.path("optionen.allowCredentials.id");
            assertTrue(erlaubte.contains(authenticator.credentialId()));
        } else {
            List<Object> erlaubte = opt.path("optionen.allowCredentials");
            assertEquals(0, erlaubte == null ? 0 : erlaubte.size());
        }

        Map<String, Object> assertion = authenticator.erstelleAnmeldeCredential(
                opt.path("optionen.challenge"),
                ORIGIN_HTTP,
                RP_ID,
                userHandleBase64Url
        );

        Response login = given()
                .contentType(ContentType.JSON)
                .cookie("_quarkus_webauthn_challenge", opt.cookie("_quarkus_webauthn_challenge"))
                .body(Map.of("credential", assertion))
                .when().post("/v1/anmelden")
                .then()
                .statusCode(200)
                .extract().response();

        String cookie = login.cookie("quarkus-credential");
        assertNotNull(cookie);
        return cookie;
    }
}
