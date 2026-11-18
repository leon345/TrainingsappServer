package de.trainingsapp.infrastructure.http;

import static de.trainingsapp.testhilfe.Ablauf.im;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import de.trainingsapp.testhilfe.Ablauf;
import de.trainingsapp.testhilfe.TestAuthenticator;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * E14, Pflichttest: genau ein {@code Set-Cookie} je Cookie mit {@code HttpOnly} und
 * {@code SameSite=Strict}; {@code Secure} hinter dem Proxy ({@code X-Forwarded-Proto: https}).
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class CookieHaertungTest {

    static final String SITZUNG = "quarkus-credential";
    static final String CHALLENGE = "_quarkus_webauthn_challenge";

    @TestHTTPResource
    URL url;

    @Test
    void registrierungAnmeldungUndAbmeldungSetzenGeharteteCookies() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        CookieFilter browser = new CookieFilter();

        Response optionen = im(browser).contentType(ContentType.JSON).body("{\"benutzername\":\"keks-eins\"}")
                .post("/v1/registrieren/optionen");
        pruefeGenauEinCookie(optionen, CHALLENGE, false);
        assertKeinCookie(optionen, SITZUNG);

        Response registriert = Ablauf.registrierungAbschliessen(browser, "keks-eins",
                geraet.registriere(new JsonObject(optionen.asString())));
        assertEquals(204, registriert.statusCode());
        pruefeGenauEinCookie(registriert, SITZUNG, false);
        pruefeGenauEinCookie(registriert, CHALLENGE, false); // Loeschung der Challenge

        CookieFilter zweiter = new CookieFilter();
        Response anmeldeOptionen = im(zweiter).post("/v1/anmelden/optionen");
        pruefeGenauEinCookie(anmeldeOptionen, CHALLENGE, false);
        Response angemeldet = Ablauf.anmeldungAbschliessen(zweiter,
                geraet.meldeAn(new JsonObject(anmeldeOptionen.asString())));
        assertEquals(204, angemeldet.statusCode());
        pruefeGenauEinCookie(angemeldet, SITZUNG, false);

        Response abgemeldet = im(zweiter).post("/v1/abmelden");
        assertEquals(204, abgemeldet.statusCode());
        pruefeGenauEinCookie(abgemeldet, SITZUNG, false);
        im(zweiter).get("/v1/passkeys").then().statusCode(401);
    }

    @Test
    void hinterDemProxyMitHttpsAuchSecure() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        CookieFilter browser = new CookieFilter();
        Response optionen = im(browser).header("X-Forwarded-Proto", "https").contentType(ContentType.JSON)
                .body("{\"benutzername\":\"keks-zwei\"}").post("/v1/registrieren/optionen");
        pruefeGenauEinCookie(optionen, CHALLENGE, true);

        // Ein Secure-Cookie sendet der CookieFilter (wie ein Browser) nicht ueber HTTP zurueck;
        // hinter dem Proxy kaeme es per HTTPS an. Deshalb hier ausdruecklich mitgeben.
        Response registriert = im(browser).header("X-Forwarded-Proto", "https")
                .cookie(CHALLENGE, optionen.getCookie(CHALLENGE)).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", "keks-zwei")
                        .put("credential", geraet.registriere(new JsonObject(optionen.asString()))).encode())
                .post("/v1/registrieren");
        assertEquals(204, registriert.statusCode());
        pruefeGenauEinCookie(registriert, SITZUNG, true);
    }

    static void pruefeGenauEinCookie(Response antwort, String name, boolean secure) {
        List<String> passende = antwort.getHeaders().getValues("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .toList();
        assertEquals(1, passende.size(), "genau ein Set-Cookie fuer " + name + ": " + passende);
        // Attributnamen sind nach RFC 6265 unabhaengig von Gross-/Kleinschreibung (Netty: "HTTPOnly")
        List<String> attribute = java.util.Arrays.stream(passende.get(0).split(";"))
                .map(a -> a.trim().toLowerCase(java.util.Locale.ROOT)).toList();
        assertTrue(attribute.contains("httponly"), passende.get(0));
        assertTrue(attribute.contains("samesite=strict"), passende.get(0));
        assertEquals(secure, attribute.contains("secure"), passende.get(0));
    }

    static void assertKeinCookie(Response antwort, String name) {
        assertFalse(antwort.getHeaders().getValues("Set-Cookie").stream().anyMatch(h -> h.startsWith(name + "=")));
    }
}
