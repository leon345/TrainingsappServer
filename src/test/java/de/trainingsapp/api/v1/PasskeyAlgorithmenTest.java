package de.trainingsapp.api.v1;

import static de.trainingsapp.testhilfe.Ablauf.anmelden;
import static de.trainingsapp.testhilfe.Ablauf.hinzufuegen;
import static de.trainingsapp.testhilfe.Ablauf.im;
import static de.trainingsapp.testhilfe.Ablauf.registrieren;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import de.trainingsapp.testhilfe.TestAuthenticator;
import de.trainingsapp.testhilfe.TestAuthenticator.Algorithmus;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import jakarta.inject.Inject;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * B4 (Nachweis, E23 Abnahme 3): RS256, EdDSA und ES256 sowie Signaturzaehler 0 durch die echte Kette
 * (Endpunkte, Extension, webauthn4j, PostgreSQL), mit dem Software-Authenticator der Tests.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PasskeyAlgorithmenTest {

    @TestHTTPResource
    URL url;

    @Inject
    DataSource dataSource;

    @ParameterizedTest
    @EnumSource(Algorithmus.class)
    void registrierenHinzufuegenUndAnmeldenJeAlgorithmus(Algorithmus algorithmus) throws SQLException {
        String name = "alg-" + algorithmus.name().toLowerCase(Locale.ROOT);
        TestAuthenticator erster = new TestAuthenticator(url, algorithmus);
        TestAuthenticator zweiter = new TestAuthenticator(url, algorithmus);
        CookieFilter browser = registrieren(name, erster);
        hinzufuegen(browser, zweiter);

        anmelden(erster);
        anmelden(zweiter);
        anmelden(erster);

        im(browser).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(2));
        long cose = switch (algorithmus) {
            case ES256 -> -7;
            case RS256 -> -257;
            case EDDSA -> -8;
        };
        assertEquals(cose, zahl("SELECT algorithmus FROM passkey WHERE credential_id = ?", erster.credentialId()));
        assertEquals(3, zahl("SELECT zaehler FROM passkey WHERE credential_id = ?", erster.credentialId()),
                "Registrierung 1, dann zwei Anmeldungen");
    }

    /** Synchronisierte Passkeys melden oft dauerhaft Zaehler 0; das muss wiederholt funktionieren. */
    @Test
    void signaturzaehlerNullWirdWiederholtAkzeptiert() throws SQLException {
        TestAuthenticator geraet = new TestAuthenticator(url, Algorithmus.ES256).zaehlerImmerNull();
        registrieren("zaehler-null", geraet);
        for (int i = 0; i < 3; i++) {
            anmelden(geraet);
        }
        assertEquals(0, zahl("SELECT zaehler FROM passkey WHERE credential_id = ?", geraet.credentialId()));
    }

    @Test
    void signaturzaehlerNullMitEdDsaUndRs256() {
        for (Algorithmus algorithmus : new Algorithmus[] {Algorithmus.EDDSA, Algorithmus.RS256}) {
            TestAuthenticator geraet = new TestAuthenticator(url, algorithmus).zaehlerImmerNull();
            registrieren("null-" + algorithmus.name().toLowerCase(Locale.ROOT), geraet);
            anmelden(geraet);
            anmelden(geraet);
        }
    }

    private long zahl(String sql, String parameter) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement(sql)) {
            abfrage.setString(1, parameter);
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getLong(1);
            }
        }
    }
}
