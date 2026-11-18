package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.webauthn.WebAuthnHardware;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * E26: Registrierung in einer Transaktion. Scheitert "Passkey speichern" nach "Benutzer anlegen"
 * (hier: Credential-ID schon registriert), bleibt kein Benutzer ohne Passkey zurueck und die
 * ausstehende Registrierung bleibt erhalten.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class RegistrierungRollbackTest {

    @TestHTTPResource
    URL url;

    @Inject
    DataSource dataSource;

    @Test
    void rollbackZwischenBenutzerAnlegenUndPasskeySpeichernHinterlaesstKeinenBenutzer() throws SQLException {
        WebAuthnHardware geraet = new WebAuthnHardware(url);
        assertEquals(204, registrieren("rolf-eins", geraet));
        assertEquals(1, zaehle("SELECT count(*) FROM benutzer WHERE benutzername = 'rolf-eins'"));

        // Dasselbe Geraet (gleiche Credential-ID) fuer einen zweiten Benutzer: legeAn gelingt,
        // fuegeHinzu scheitert, die Transaktion rollt beides zurueck.
        assertEquals(400, registrieren("rolf-zwei", geraet));

        assertEquals(0, zaehle("SELECT count(*) FROM benutzer WHERE benutzername = 'rolf-zwei'"));
        assertEquals(0, zaehle("SELECT count(*) FROM benutzer b WHERE NOT EXISTS "
                + "(SELECT 1 FROM passkey p WHERE p.benutzer_id = b.id)"), "Benutzer ohne Passkey");
        assertEquals(1, zaehle("SELECT count(*) FROM ausstehende_registrierung WHERE benutzername = 'rolf-zwei'"),
                "Reservierung durch Rollback wiederhergestellt");
    }

    private int registrieren(String name, WebAuthnHardware geraet) {
        CookieFilter browser = new CookieFilter();
        String challenge = new JsonObject(given().filter(browser).contentType(ContentType.JSON)
                .body("{\"benutzername\":\"" + name + "\"}").post("/v1/registrieren/optionen")
                .then().statusCode(200).extract().asString()).getString("challenge");
        return given().filter(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name)
                        .put("credential", geraet.makeRegistrationJson(challenge)).encode())
                .post("/v1/registrieren").then().extract().statusCode();
    }

    private int zaehle(String sql) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement(sql);
                ResultSet ergebnis = abfrage.executeQuery()) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
