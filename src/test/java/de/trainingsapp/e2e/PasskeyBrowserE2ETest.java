package de.trainingsapp.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * B4 (E23, Abnahme 3): E2E mit dem virtuellen Authenticator von Chromium (CDP, Domain "WebAuthn") gegen
 * eine Testseite, die nur in den Testquellen existiert (src/test/resources/META-INF/resources/e2e-passkeys).
 * Belegt, was der Software-Authenticator nicht belegen kann:
 * <ul>
 *   <li>das JSON-Format der Optionen und Credentials, wie Chromium es mit
 *       {@code parseCreationOptionsFromJSON}/{@code parseRequestOptionsFromJSON} und {@code toJSON()} verarbeitet;</li>
 *   <li>{@code excludeCredentials} verhindert, dass derselbe Authenticator zweimal registriert wird;</li>
 *   <li>zwei Passkeys je Benutzer, Anmeldung mit jedem ohne Benutzernamen (discoverable credential);</li>
 *   <li>Cookies im Browser: HttpOnly (per JavaScript nicht lesbar), SameSite=Strict;
 *       schreibende Aufrufe aus dem Browser bestehen die Origin-Pruefung.</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
@E2eErforderlich
class PasskeyBrowserE2ETest {

    private static final Logger LOG = Logger.getLogger(PasskeyBrowserE2ETest.class);

    @TestHTTPResource
    URL url;

    static Playwright playwright;
    static Browser browser;

    @BeforeAll
    static void browserStarten() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        LOG.infof("E2E mit Chromium %s (%s)", browser.version(), playwright.chromium().executablePath());
    }

    @AfterAll
    static void browserBeenden() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Test
    void zweiPasskeysMitExcludeCredentialsUndAnmeldungOhneBenutzernamen() {
        try (BrowserContext kontext = browser.newContext()) {
            Page seite = kontext.newPage();
            seite.navigate(url + "e2e-passkeys/index.html");
            seite.waitForFunction("() => window.e2e !== undefined");
            CDPSession cdp = kontext.newCDPSession(seite);
            cdp.send("WebAuthn.enable", json(Map.of("enableUI", false)));

            // 1. Registrierung mit Authenticator A (Plattform, resident key, UV)
            String a = authenticatorHinzufuegen(cdp);
            JsonObject registrierung = aufruf(seite, "n => window.e2e.registrieren(n)", "e2e-anna");
            assertEquals("abschluss", registrierung.getString("schritt"), registrierung.encode());
            assertEquals(204, registrierung.getInteger("status"), registrierung.encode());
            pruefeErzeugungsoptionen(registrierung.getJsonObject("optionen"));
            pruefeRegistrierungsCredential(registrierung.getJsonObject("credential"));
            String idA = registrierung.getJsonObject("credential").getString("id");

            // 2. Zweiter Passkey auf demselben Authenticator: excludeCredentials -> InvalidStateError
            JsonObject doppelt = aufruf(seite, "() => window.e2e.hinzufuegen()", null);
            assertEquals("create", doppelt.getString("schritt"), doppelt.encode());
            assertEquals("InvalidStateError", doppelt.getString("fehler"), doppelt.encode());
            assertEquals(idA, doppelt.getJsonObject("optionen").getJsonArray("excludeCredentials")
                    .getJsonObject(0).getString("id"));
            com.google.gson.JsonArray credentialsA = credentials(cdp, a);
            assertEquals(1, credentialsA.size(), "kein zweiter Passkey auf Authenticator A");

            // 3. Zweiter Passkey auf Authenticator B (A ist nicht mehr verbunden)
            entfernen(cdp, a);
            String b = authenticatorHinzufuegen(cdp);
            JsonObject zweiter = aufruf(seite, "() => window.e2e.hinzufuegen()", null);
            assertEquals(201, zweiter.getInteger("status"), zweiter.encode());
            assertEquals(1, zweiter.getJsonObject("optionen").getJsonArray("excludeCredentials").size());
            com.google.gson.JsonArray credentialsB = credentials(cdp, b);
            entfernen(cdp, b);

            // 4. Anmeldung ohne Benutzernamen in einem frischen Browserzustand, je einmal mit A und mit B
            for (com.google.gson.JsonArray gespeichert : List.of(credentialsA, credentialsB)) {
                kontext.clearCookies();
                String geraet = authenticatorHinzufuegen(cdp);
                cdp.send("WebAuthn.addCredential", json(Map.of("authenticatorId", geraet,
                        "credential", gespeichert.get(0))));
                JsonObject anmeldung = aufruf(seite, "() => window.e2e.anmelden()", null);
                assertEquals("abschluss", anmeldung.getString("schritt"), anmeldung.encode());
                assertEquals(204, anmeldung.getInteger("status"), anmeldung.encode());
                pruefeAnforderungsoptionen(anmeldung.getJsonObject("optionen"));
                assertNotNull(anmeldung.getJsonObject("credential").getJsonObject("response").getString("userHandle"),
                        "discoverable credential liefert den User-Handle");
                JsonObject liste = aufruf(seite, "() => window.e2e.liste()", null);
                assertEquals(200, liste.getInteger("status"));
                assertEquals(2, new io.vertx.core.json.JsonArray(liste.getString("text")).size());
                entfernen(cdp, geraet);
            }

            // 5. Cookies: HttpOnly (fuer Skripte unsichtbar) und SameSite=Strict
            String fuerSkript = (String) seite.evaluate("() => window.e2e.cookiesFuerSkript()");
            assertFalse(fuerSkript.contains("quarkus-credential"), fuerSkript);
            assertFalse(fuerSkript.contains("_quarkus_webauthn_challenge"), fuerSkript);
            Cookie sitzung = kontext.cookies().stream().filter(c -> c.name.equals("quarkus-credential"))
                    .findFirst().orElseThrow();
            assertTrue(sitzung.httpOnly);
            assertEquals(SameSiteAttribute.STRICT, sitzung.sameSite);

            // 6. Schreibende Aufrufe aus dem Browser bestehen die Origin-Pruefung (Origin setzt der Browser)
            JsonObject liste = new JsonObject().put("a", new io.vertx.core.json.JsonArray(
                    aufruf(seite, "() => window.e2e.liste()", null).getString("text")));
            String erste = liste.getJsonArray("a").getJsonObject(0).getString("id");
            JsonObject umbenannt = aufruf(seite, "a => window.e2e.umbenennen(a[0], a[1])", List.of(erste, "Laptop"));
            assertEquals(200, umbenannt.getInteger("status"), umbenannt.encode());
            assertEquals(204, aufruf(seite, "() => window.e2e.abmelden()", null).getInteger("status"));
            assertEquals(401, aufruf(seite, "() => window.e2e.liste()", null).getInteger("status"));
        }
    }

    /** Optionen fuer navigator.credentials.create(), wie Chromium sie angenommen hat. */
    private static void pruefeErzeugungsoptionen(JsonObject o) {
        assertEquals("localhost", o.getJsonObject("rp").getString("id"));
        assertEquals(16, Base64.getUrlDecoder().decode(o.getJsonObject("user").getString("id")).length);
        assertEquals("e2e-anna", o.getJsonObject("user").getString("name"));
        assertFalse(o.getString("challenge").contains("="), "Base64URL ohne Padding");
        List<Object> algorithmen = o.getJsonArray("pubKeyCredParams").stream()
                .map(p -> ((JsonObject) p).getValue("alg")).map(v -> ((Number) v).intValue()).map(Object.class::cast)
                .toList();
        assertEquals(List.of(-8, -7, -257), algorithmen);
        assertEquals("required", o.getJsonObject("authenticatorSelection").getString("residentKey"));
        assertEquals("required", o.getJsonObject("authenticatorSelection").getString("userVerification"));
    }

    private static void pruefeAnforderungsoptionen(JsonObject o) {
        assertEquals("localhost", o.getString("rpId"));
        assertTrue(o.getJsonArray("allowCredentials") == null || o.getJsonArray("allowCredentials").isEmpty(),
                "keine allowCredentials: Anmeldung ohne Benutzernamen");
        assertEquals("required", o.getString("userVerification"));
    }

    /** Credential aus credential.toJSON() nach navigator.credentials.create(). */
    private static void pruefeRegistrierungsCredential(JsonObject c) {
        assertEquals("public-key", c.getString("type"));
        assertEquals(c.getString("id"), c.getString("rawId"));
        JsonObject r = c.getJsonObject("response");
        assertNotNull(r.getString("clientDataJSON"));
        assertNotNull(r.getString("attestationObject"));
        assertNotNull(r.getValue("publicKeyAlgorithm"));
        assertNotNull(c.getJsonObject("clientExtensionResults"));
    }

    private static String authenticatorHinzufuegen(CDPSession cdp) {
        com.google.gson.JsonObject antwort = cdp.send("WebAuthn.addVirtualAuthenticator", json(Map.of("options", Map.of(
                "protocol", "ctap2", "transport", "internal", "hasResidentKey", true,
                "hasUserVerification", true, "isUserVerified", true, "automaticPresenceSimulation", true))));
        return antwort.get("authenticatorId").getAsString();
    }

    private static com.google.gson.JsonArray credentials(CDPSession cdp, String authenticator) {
        return cdp.send("WebAuthn.getCredentials", json(Map.of("authenticatorId", authenticator)))
                .getAsJsonArray("credentials");
    }

    private static void entfernen(CDPSession cdp, String authenticator) {
        cdp.send("WebAuthn.removeVirtualAuthenticator", json(Map.of("authenticatorId", authenticator)));
    }

    private static JsonObject aufruf(Page seite, String funktion, Object argument) {
        Object ergebnis = seite.evaluate("async (arg) => JSON.stringify(await (" + funktion + ")(arg))", argument);
        return new JsonObject((String) ergebnis);
    }

    private static com.google.gson.JsonObject json(Object wert) {
        Gson gson = new Gson();
        return gson.toJsonTree(wert).getAsJsonObject();
    }
}
