package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
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
import io.quarkus.test.security.webauthn.WebAuthnHardware;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;

/**
 * Akzeptanztest E3 gegen PostgreSQL (E23, Abnahme 1): Ein Benutzer registriert zwei Passkeys,
 * meldet sich mit jedem an, listet sie, loescht einen; den letzten kann er nicht loeschen.
 * Benutzer, Passkeys und ausstehende Registrierungen liegen in der Datenbank (Flyway V2 ff.).
 * Authenticator: Software-Authenticator {@link WebAuthnHardware} der Extension
 * (fmt "none", ES256, rpId localhost). Jeder {@link CookieFilter} entspricht einem Browser.
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
        WebAuthnHardware handy = new WebAuthnHardware(url);
        WebAuthnHardware laptop = new WebAuthnHardware(url);

        // 1a. Registrierung mit dem ersten Passkey (unauthentifiziert) -> angemeldet
        CookieFilter browser1 = new CookieFilter();
        JsonObject optionen1 = new JsonObject(given().filter(browser1).contentType(ContentType.JSON)
                .body("{\"benutzername\":\"anna\"}")
                .post("/v1/registrieren/optionen")
                .then().statusCode(200).cookie("_quarkus_webauthn_challenge", notNullValue())
                .extract().asString());
        given().filter(browser1).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", "anna")
                        .put("credential", handy.makeRegistrationJson(optionen1.getString("challenge"))).encode())
                .post("/v1/registrieren")
                .then().statusCode(204).cookie("quarkus-credential", notNullValue());

        // 1b. Angemeldet einen zweiten Passkey hinzufuegen
        JsonObject optionen2 = new JsonObject(given().filter(browser1)
                .post("/v1/passkeys/optionen")
                .then().statusCode(200).extract().asString());
        // gleicher User-Handle wie beim ersten Passkey, erster Passkey ausgeschlossen
        assertEquals(optionen1.getJsonObject("user").getString("id"),
                optionen2.getJsonObject("user").getString("id"));
        assertEquals(1, optionen2.getJsonArray("excludeCredentials").size());
        // User-Handle = 16 Rohbytes der BenutzerId (E18)
        assertEquals(16, Base64.getUrlDecoder().decode(optionen1.getJsonObject("user").getString("id")).length);
        String laptopId = given().filter(browser1).contentType(ContentType.JSON)
                .body(laptop.makeRegistrationJson(optionen2.getString("challenge")).encode())
                .post("/v1/passkeys")
                .then().statusCode(201).extract().path("id");

        // 2. Mit jedem Passkey in einem frischen Browser anmelden
        CookieFilter browserHandy = anmelden(handy);
        CookieFilter browserLaptop = anmelden(laptop);

        // 3. Auflisten: beide Passkeys, aus beiden Sitzungen gleich (gleicher Benutzer)
        List<String> ids = given().filter(browserHandy).get("/v1/passkeys")
                .then().statusCode(200).body("$", hasSize(2)).extract().path("id");
        given().filter(browserLaptop).get("/v1/passkeys")
                .then().statusCode(200).body("id", containsInAnyOrder(ids.toArray()));
        String handyId = ids.stream().filter(id -> !id.equals(laptopId)).findFirst().orElseThrow();
        assertNotEquals(handyId, laptopId);

        // 4. Einen loeschen (den Handy-Passkey, aus der Laptop-Sitzung)
        given().filter(browserLaptop).delete("/v1/passkeys/" + handyId).then().statusCode(204);
        given().filter(browserLaptop).get("/v1/passkeys")
                .then().statusCode(200).body("$", hasSize(1)).body("[0].id", equalTo(laptopId));
        // geloeschter Passkey kann sich nicht mehr anmelden
        CookieFilter browser3 = new CookieFilter();
        String ch = anmeldeChallenge(browser3);
        given().filter(browser3).contentType(ContentType.JSON).body(handy.makeLoginJson(ch).encode())
                .post("/v1/anmelden").then().statusCode(400);

        // 5. Den letzten kann er nicht loeschen
        given().filter(browserLaptop).delete("/v1/passkeys/" + laptopId)
                .then().statusCode(409).body("code", equalTo("LETZTER_PASSKEY"));
        given().filter(browserLaptop).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(1));
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
        given().get("/v1/passkeys").then().statusCode(401);
        given().post("/v1/passkeys/optionen").then().statusCode(401);
        given().delete("/v1/passkeys/abc").then().statusCode(401);
    }

    @Test
    void fremdenPasskeyLoeschenIst404() {
        WebAuthnHardware a = new WebAuthnHardware(url);
        WebAuthnHardware b = new WebAuthnHardware(url);
        CookieFilter browserA = registrieren("bert", a);
        CookieFilter browserB = registrieren("carla", b);
        String idVonB = given().filter(browserB).get("/v1/passkeys").then().extract().path("[0].id");
        given().filter(browserA).delete("/v1/passkeys/" + idVonB).then().statusCode(404);
    }

    @Test
    void benutzernameDoppeltIst409() {
        registrieren("dora", new WebAuthnHardware(url));
        given().contentType(ContentType.JSON).body("{\"benutzername\":\"dora\"}")
                .post("/v1/registrieren/optionen").then().statusCode(409);
    }

    @Test
    void benutzernameMitKlammeraffeIst400() {
        // E17: Benutzernamen sind Pseudonyme, keine E-Mail-Adressen
        given().contentType(ContentType.JSON).body("{\"benutzername\":\"anna@example.org\"}")
                .post("/v1/registrieren/optionen")
                .then().statusCode(400).body("code", equalTo("BENUTZERNAME_UNGUELTIG"))
                .header("Set-Cookie", nullValue());
    }

    @Test
    void registrierungOhneChallengeIst400() {
        given().contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", "emil")
                        .put("credential", new WebAuthnHardware(url).makeRegistrationJson("gefaelscht")).encode())
                .post("/v1/registrieren").then().statusCode(400);
    }

    private CookieFilter registrieren(String name, WebAuthnHardware hw) {
        CookieFilter browser = new CookieFilter();
        String ch = new JsonObject(given().filter(browser).contentType(ContentType.JSON)
                .body("{\"benutzername\":\"" + name + "\"}").post("/v1/registrieren/optionen")
                .then().statusCode(200).extract().asString()).getString("challenge");
        given().filter(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name).put("credential", hw.makeRegistrationJson(ch)).encode())
                .post("/v1/registrieren").then().statusCode(204);
        return browser;
    }

    private CookieFilter anmelden(WebAuthnHardware hw) {
        CookieFilter browser = new CookieFilter();
        String ch = anmeldeChallenge(browser);
        given().filter(browser).contentType(ContentType.JSON).body(hw.makeLoginJson(ch).encode())
                .post("/v1/anmelden")
                .then().statusCode(204).cookie("quarkus-credential", notNullValue());
        return browser;
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

    private String anmeldeChallenge(CookieFilter browser) {
        return new JsonObject(given().filter(browser).post("/v1/anmelden/optionen")
                .then().statusCode(200).extract().asString()).getString("challenge");
    }
}
