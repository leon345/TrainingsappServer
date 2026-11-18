package de.trainingsapp.api.v1;

import static de.trainingsapp.testhilfe.Ablauf.anmeldeOptionen;
import static de.trainingsapp.testhilfe.Ablauf.anmelden;
import static de.trainingsapp.testhilfe.Ablauf.anmeldungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.hinzufuegenOptionen;
import static de.trainingsapp.testhilfe.Ablauf.im;
import static de.trainingsapp.testhilfe.Ablauf.registrieren;
import static de.trainingsapp.testhilfe.Ablauf.registrierungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.registrierungsOptionen;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import de.trainingsapp.testhilfe.TestAuthenticator;
import de.trainingsapp.testhilfe.TestOrigin;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * B1: Sicherheitseigenschaften der beiden Zeremonien (Registrierung, Anmeldung) und des
 * Hinzufuegens. Jeder Test beschreibt eine Luecke oder eine geprueft vorhandene Eigenschaft.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PasskeyZeremonieSicherheitTest {

    private static final String CHALLENGE_COOKIE = "_quarkus_webauthn_challenge";

    @TestHTTPResource
    URL url;

    // --- Registrierung: Benutzername an den Start gebunden (Luecke, geschlossen) ---

    @Test
    void abschlussMitAnderemBenutzernamenAlsBeimStartScheitert() {
        // Ein anderer Ablauf hat "opfer" reserviert und noch nicht abgeschlossen.
        CookieFilter opferBrowser = new CookieFilter();
        TestAuthenticator opferGeraet = new TestAuthenticator(url);
        JsonObject opferOptionen = registrierungsOptionen(opferBrowser, "opfer");

        // Der Angreifer startet fuer "taeter" und schliesst mit dem Namen "opfer" ab.
        CookieFilter taeterBrowser = new CookieFilter();
        TestAuthenticator taeterGeraet = new TestAuthenticator(url);
        JsonObject taeterOptionen = registrierungsOptionen(taeterBrowser, "taeter");
        registrierungAbschliessen(taeterBrowser, "opfer", taeterGeraet.registriere(taeterOptionen))
                .then().statusCode(400).body("code", equalTo("PASSKEY_UNGUELTIG"));

        // Die Reservierung von "opfer" ist unberuehrt; sein eigener Abschluss gelingt mit seinem User-Handle.
        registrierungAbschliessen(opferBrowser, "opfer", opferGeraet.registriere(opferOptionen)).then().statusCode(204);
        anmelden(opferGeraet);
    }

    @Test
    void abschlussOhneReservierungFuerDenNamenScheitert() {
        CookieFilter browser = new CookieFilter();
        JsonObject optionen = registrierungsOptionen(browser, "start-name");
        registrierungAbschliessen(browser, "anderer-name", new TestAuthenticator(url).registriere(optionen))
                .then().statusCode(400);
    }

    // --- Anmeldung: User-Handle muss zum Besitzer passen (Luecke, geschlossen) ---

    @Test
    void anmeldungMitFremdemUserHandleScheitert() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("handle-eins", geraet);
        TestAuthenticator anderes = new TestAuthenticator(url);
        registrieren("handle-zwei", anderes);

        geraet.mitUserHandle(anderes.userHandle());
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser)))
                .then().statusCode(400).body("code", equalTo("PASSKEY_UNGUELTIG"));
    }

    @Test
    void anmeldungMitZufaelligemUserHandleScheitert() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("handle-drei", geraet);
        byte[] zufall = new byte[16];
        new java.security.SecureRandom().nextBytes(zufall);
        geraet.mitUserHandle(zufall);
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser))).then().statusCode(400);
    }

    @Test
    void anmeldungOhneUserHandleScheitert() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("handle-vier", geraet);
        geraet.ohneUserHandle();
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser))).then().statusCode(400);
    }

    // --- Challenge einmalig (Luecke bei Zaehler 0, geschlossen) ---

    @Test
    void wiedereingereichteAnmeldungMitZaehlerNullScheitert() {
        TestAuthenticator geraet = new TestAuthenticator(url).zaehlerImmerNull();
        registrieren("replay-null", geraet);

        CookieFilter browser = new CookieFilter();
        Response optionen = im(browser).post("/v1/anmelden/optionen");
        String challengeCookie = optionen.getCookie(CHALLENGE_COOKIE);
        assertNotNull(challengeCookie);
        JsonObject antwort = geraet.meldeAn(new JsonObject(optionen.asString()));
        anmeldungAbschliessen(browser, antwort).then().statusCode(204);

        // Angreifer mit abgefangenem Cookie und abgefangener Antwort
        given().header("Origin", TestOrigin.wert()).cookie(CHALLENGE_COOKIE, challengeCookie)
                .contentType(ContentType.JSON).body(antwort.encode()).post("/v1/anmelden")
                .then().statusCode(400);
    }

    @Test
    void jederOptionenAbrufLiefertEineNeueChallenge() {
        CookieFilter browser = new CookieFilter();
        String erste = anmeldeOptionen(browser).getString("challenge");
        String zweite = anmeldeOptionen(browser).getString("challenge");
        assertNotEquals(erste, zweite, "die Extension wuerde ohne Gegenmassnahme die Cookie-Challenge wiederverwenden");
    }

    // --- Signaturzaehler (Eigenschaft der Extension, geprueft) ---

    @Test
    void zurueckgehenderSignaturzaehlerWirdAbgelehnt() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("zaehler", geraet);
        CookieFilter b1 = new CookieFilter();
        JsonObject frueher = geraet.meldeAn(anmeldeOptionen(b1)); // Zaehler 2
        CookieFilter b2 = new CookieFilter();
        anmeldungAbschliessen(b2, geraet.meldeAn(anmeldeOptionen(b2))).then().statusCode(204); // Zaehler 3
        anmeldungAbschliessen(b1, frueher).then().statusCode(400);
    }

    // --- Origin und rpId (Eigenschaft der Extension, geprueft) ---

    @Test
    void fremderOriginWirdAbgelehnt() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("origin-test", geraet);
        geraet.mitOrigin("https://evil.example");
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser))).then().statusCode(400);

        CookieFilter neu = new CookieFilter();
        JsonObject optionen = registrierungsOptionen(neu, "origin-neu");
        registrierungAbschliessen(neu, "origin-neu",
                new TestAuthenticator(url).mitOrigin("https://evil.example").registriere(optionen))
                .then().statusCode(400);
    }

    @Test
    void fremdeRpIdWirdAbgelehnt() {
        TestAuthenticator geraet = new TestAuthenticator(url);
        registrieren("rpid-test", geraet);
        geraet.mitRpId("evil.example");
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser))).then().statusCode(400);
    }

    // --- Hinzufuegen nur fuer den angemeldeten Benutzer (geprueft) ---

    @Test
    void hinzugefuegterPasskeyGehoertImmerDemAngemeldetenBenutzer() {
        CookieFilter anna = registrieren("hinzu-anna", new TestAuthenticator(url));
        CookieFilter bert = registrieren("hinzu-bert", new TestAuthenticator(url));
        String bertHandle = hinzufuegenOptionen(bert).getJsonObject("user").getString("id");

        // Anna holt Optionen und schickt eine Antwort mit Berts User-Handle: der Passkey landet bei Anna
        TestAuthenticator geraet = new TestAuthenticator(url);
        JsonObject optionen = hinzufuegenOptionen(anna);
        assertNotEquals(bertHandle, optionen.getJsonObject("user").getString("id"));
        im(anna).contentType(ContentType.JSON).body(geraet.registriere(optionen).encode())
                .post("/v1/passkeys").then().statusCode(201);
        im(anna).get("/v1/passkeys").then().body("$", hasSize(2));
        im(bert).get("/v1/passkeys").then().body("$", hasSize(1));

        // Eine Antwort auf eine Challenge ohne Sitzung ist nutzlos
        given().header("Origin", TestOrigin.wert()).contentType(ContentType.JSON)
                .body(new TestAuthenticator(url).registriere(optionen).encode())
                .post("/v1/passkeys").then().statusCode(401);
    }

    @Test
    void registrierungsAntwortKannNichtZweimalVerwendetWerden() {
        CookieFilter browser = new CookieFilter();
        String name = "einmal-" + UUID.randomUUID().toString().substring(0, 6);
        Response optionen = im(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name).encode()).post("/v1/registrieren/optionen");
        String challengeCookie = optionen.getCookie(CHALLENGE_COOKIE);
        JsonObject antwort = new TestAuthenticator(url).registriere(new JsonObject(optionen.asString()));
        registrierungAbschliessen(browser, name, antwort).then().statusCode(204);

        given().header("Origin", TestOrigin.wert()).cookie(CHALLENGE_COOKIE, challengeCookie)
                .contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name).put("credential", antwort).encode())
                .post("/v1/registrieren").then().statusCode(400);
    }
}
