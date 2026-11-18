package de.trainingsapp.api.v1;

import static de.trainingsapp.testhilfe.Ablauf.anmeldeOptionen;
import static de.trainingsapp.testhilfe.Ablauf.anmelden;
import static de.trainingsapp.testhilfe.Ablauf.anmeldungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.hinzufuegen;
import static de.trainingsapp.testhilfe.Ablauf.hinzufuegenOptionen;
import static de.trainingsapp.testhilfe.Ablauf.im;
import static de.trainingsapp.testhilfe.Ablauf.registrieren;
import static de.trainingsapp.testhilfe.Ablauf.registrierungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.registrierungsOptionen;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.quarkus.test.common.http.TestHTTPResource;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import de.trainingsapp.testhilfe.TestAuthenticator;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;

/**
 * Akzeptanztest E3 gegen PostgreSQL (E23, Abnahme 1): Ein Benutzer registriert zwei Passkeys,
 * meldet sich mit jedem an, listet sie, loescht einen; den letzten kann er nicht loeschen.
 * Benutzer, Passkeys und ausstehende Registrierungen liegen in der Datenbank (Flyway V2 ff.).
 * Authenticator: Software-Authenticator {@link TestAuthenticator}
 * (fmt "none", ES256, rpId localhost, mit User-Handle). Jeder {@link CookieFilter} entspricht einem Browser.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PasskeyAkzeptanzTest {

    @TestHTTPResource
    URL url;

    @Inject
    DataSource dataSource;

    @Test
    void zweiPasskeysRegistrierenAnmeldenListenLoeschenLetzterGesperrt() throws SQLException {
        TestAuthenticator handy = new TestAuthenticator(url);
        TestAuthenticator laptop = new TestAuthenticator(url);

        // 1a. Registrierung mit dem ersten Passkey (unauthentifiziert) -> angemeldet
        CookieFilter browser1 = new CookieFilter();
        JsonObject optionen1 = registrierungsOptionen(browser1, "anna");
        registrierungAbschliessen(browser1, "anna", handy.registriere(optionen1))
                .then().statusCode(204).cookie("quarkus-credential", notNullValue());

        // 1b. Angemeldet einen zweiten Passkey hinzufuegen
        JsonObject optionen2 = hinzufuegenOptionen(browser1);
        // gleicher User-Handle wie beim ersten Passkey, erster Passkey ausgeschlossen
        assertEquals(optionen1.getJsonObject("user").getString("id"),
                optionen2.getJsonObject("user").getString("id"));
        assertEquals(1, optionen2.getJsonArray("excludeCredentials").size());
        assertEquals(handy.credentialId(), optionen2.getJsonArray("excludeCredentials").getJsonObject(0).getString("id"));
        // User-Handle = 16 Rohbytes der BenutzerId (E18)
        assertEquals(16, Base64.getUrlDecoder().decode(optionen1.getJsonObject("user").getString("id")).length);
        String laptopId = im(browser1).contentType(ContentType.JSON)
                .body(laptop.registriere(optionen2).encode())
                .post("/v1/passkeys")
                .then().statusCode(201).extract().path("id");

        // 2. Mit jedem Passkey in einem frischen Browser anmelden (ohne Benutzernamen)
        CookieFilter browserHandy = anmelden(handy);
        CookieFilter browserLaptop = anmelden(laptop);

        // 3. Auflisten: beide Passkeys, aus beiden Sitzungen gleich (gleicher Benutzer);
        //    automatische Bezeichnungen und Tag der letzten Anmeldung (E29)
        String heute = LocalDate.now(ZoneOffset.UTC).toString();
        List<String> ids = im(browserHandy).get("/v1/passkeys")
                .then().statusCode(200).body("$", hasSize(2))
                .body("bezeichnung", containsInAnyOrder("Passkey 1", "Passkey 2"))
                .body("erstelltAm", everyItem(equalTo(heute)))
                .body("zuletztVerwendetAm", everyItem(equalTo(heute)))
                .extract().path("id");
        im(browserLaptop).get("/v1/passkeys")
                .then().statusCode(200).body("id", containsInAnyOrder(ids.toArray()));
        String handyId = ids.stream().filter(id -> !id.equals(laptopId)).findFirst().orElseThrow();
        assertNotEquals(handyId, laptopId);

        // 4. Einen loeschen (den Handy-Passkey, aus der Laptop-Sitzung)
        im(browserLaptop).delete("/v1/passkeys/" + handyId).then().statusCode(204);
        im(browserLaptop).get("/v1/passkeys")
                .then().statusCode(200).body("$", hasSize(1)).body("[0].id", equalTo(laptopId));
        // geloeschter Passkey kann sich nicht mehr anmelden
        CookieFilter browser3 = new CookieFilter();
        anmeldungAbschliessen(browser3, handy.meldeAn(anmeldeOptionen(browser3))).then().statusCode(400);

        // 5. Den letzten kann er nicht loeschen
        im(browserLaptop).delete("/v1/passkeys/" + laptopId)
                .then().statusCode(409).body("code", equalTo("LETZTER_PASSKEY"));
        im(browserLaptop).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(1));
        anmelden(laptop);

        // 6. Beleg direkt aus PostgreSQL (an Hibernate vorbei): ein Benutzer mit genau dem
        //    verbliebenen Passkey, keine ausstehende Registrierung mehr
        assertEquals(List.of(laptopId), sql("""
                SELECT p.credential_id FROM passkey p JOIN benutzer b ON b.id = p.benutzer_id
                WHERE b.benutzername = 'anna'"""));
        assertEquals(List.of(), sql("SELECT id FROM ausstehende_registrierung WHERE benutzername = 'anna'"));
    }

    @Test
    void verwaltungOhneSitzungIst401() {
        CookieFilter ohneSitzung = new CookieFilter();
        im(ohneSitzung).get("/v1/passkeys").then().statusCode(401);
        im(ohneSitzung).post("/v1/passkeys/optionen").then().statusCode(401);
        im(ohneSitzung).delete("/v1/passkeys/abc").then().statusCode(401);
        im(ohneSitzung).contentType(ContentType.JSON).body("{\"bezeichnung\":\"x\"}").patch("/v1/passkeys/abc")
                .then().statusCode(401);
    }

    @Test
    void umbenennenNurEigenerPasskeysMitGueltigerBezeichnung() {
        CookieFilter browserA = registrieren("ulla", new TestAuthenticator(url));
        CookieFilter browserB = registrieren("udo", new TestAuthenticator(url));
        String idVonA = im(browserA).get("/v1/passkeys").then().extract().path("[0].id");

        // fremder Passkey: 404, Bezeichnung unveraendert
        im(browserB).contentType(ContentType.JSON).body("{\"bezeichnung\":\"gekapert\"}")
                .patch("/v1/passkeys/" + idVonA).then().statusCode(404).body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));
        im(browserA).get("/v1/passkeys").then().body("[0].bezeichnung", equalTo("Passkey 1"));

        // ungueltige Bezeichnungen: 400
        for (String ungueltig : List.of("", "   ", "a".repeat(51), "Zero\u200Bwidth")) {
            im(browserA).contentType(ContentType.JSON)
                    .body(new JsonObject().put("bezeichnung", ungueltig).encode())
                    .patch("/v1/passkeys/" + idVonA).then().statusCode(400).body("code", equalTo("BEZEICHNUNG_UNGUELTIG"));
        }

        // eigener Passkey: 200, NFC und Trim
        im(browserA).contentType(ContentType.JSON)
                .body(new JsonObject().put("bezeichnung", "  Schlu\u0308ssel im Bu\u0308ro ").encode())
                .patch("/v1/passkeys/" + idVonA)
                .then().statusCode(200).body("bezeichnung", equalTo("Schl\u00FCssel im B\u00FCro"));
        im(browserA).get("/v1/passkeys").then().body("[0].bezeichnung", equalTo("Schl\u00FCssel im B\u00FCro"));
    }

    @Test
    void geloeschteNummernWerdenNichtWiederverwendet() {
        CookieFilter browser = registrieren("nora", new TestAuthenticator(url));
        String zweiterId = hinzufuegen(browser, new TestAuthenticator(url));
        im(browser).delete("/v1/passkeys/" + zweiterId).then().statusCode(204);

        hinzufuegen(browser, new TestAuthenticator(url));

        im(browser).get("/v1/passkeys").then().statusCode(200)
                .body("bezeichnung", containsInAnyOrder("Passkey 1", "Passkey 3"));
    }

    @Test
    void fremdenPasskeyLoeschenIst404() {
        CookieFilter browserA = registrieren("bert", new TestAuthenticator(url));
        CookieFilter browserB = registrieren("carla", new TestAuthenticator(url));
        String idVonB = im(browserB).get("/v1/passkeys").then().extract().path("[0].id");
        im(browserA).delete("/v1/passkeys/" + idVonB).then().statusCode(404);
    }

    @Test
    void benutzernameDoppeltIst409() {
        registrieren("dora", new TestAuthenticator(url));
        im(new CookieFilter()).contentType(ContentType.JSON).body("{\"benutzername\":\"dora\"}")
                .post("/v1/registrieren/optionen").then().statusCode(409);
    }

    @Test
    void benutzernameMitKlammeraffeIst400() {
        // E17: Benutzernamen sind Pseudonyme, keine E-Mail-Adressen
        im(new CookieFilter()).contentType(ContentType.JSON).body("{\"benutzername\":\"anna@example.org\"}")
                .post("/v1/registrieren/optionen")
                .then().statusCode(400).body("code", equalTo("BENUTZERNAME_UNGUELTIG"))
                .header("Set-Cookie", nullValue());
    }

    @Test
    void registrierungOhneChallengeIst400() {
        JsonObject gefaelscht = new JsonObject().put("challenge", "Z2VmYWVsc2NodA")
                .put("user", new JsonObject().put("id", "AAAAAAAAAAAAAAAAAAAAAA"));
        registrierungAbschliessen(new CookieFilter(), "emil", new TestAuthenticator(url).registriere(gefaelscht))
                .then().statusCode(400);
    }

    private List<String> sql(String abfrage) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement anweisung = verbindung.prepareStatement(abfrage);
                ResultSet ergebnis = anweisung.executeQuery()) {
            List<String> zeilen = new ArrayList<>();
            while (ergebnis.next()) {
                zeilen.add(ergebnis.getString(1));
            }
            return zeilen;
        }
    }
}
