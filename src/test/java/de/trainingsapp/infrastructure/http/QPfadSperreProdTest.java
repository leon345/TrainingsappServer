package de.trainingsapp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * E16 im Prod-Profil: 404 ohne Body und ohne Set-Cookie fuer /q/* und /.well-known/webauthn,
 * bevor irgendein Handler, ein Datenbankzugriff oder der Permission-Check stattfindet.
 */
@QuarkusTest
@TestProfile(ProdProfil.class)
@DatenbankErforderlich
class QPfadSperreProdTest {

    @Inject
    SessionFactory sessionFactory;

    @Inject
    DataSource dataSource;

    @ParameterizedTest
    @ValueSource(strings = {
            "/q/openapi", "/q/openapi?format=json", "/q/swagger-ui", "/q/swagger-ui/", "/q/health",
            "/q/webauthn/register-options-challenge?username=probe",
            "/q/webauthn/login-options-challenge", "/q/webauthn/webauthn.js", "/q/webauthn/logout",
            "/.well-known/webauthn", "/q", "/q/gibt-es-nicht"})
    void gesperrt404OhneBodyOhneCookieOhneDatenbank(String pfad) throws SQLException {
        Statistics statistik = sessionFactory.getStatistics();
        long statementsVorher = statistik.getPrepareStatementCount();
        long sessionsVorher = statistik.getSessionOpenCount();
        int reservierungenVorher = reservierungen();

        for (String methode : new String[] {"GET", "POST", "HEAD"}) {
            Response antwort = given().redirects().follow(false).header("Origin", "http://localhost:8081")
                    .request(methode, pfad);
            assertEquals(404, antwort.statusCode(), methode + " " + pfad);
            assertEquals("", antwort.asString(), "kein Body: " + methode + " " + pfad);
            assertTrue(antwort.getHeaders().getValues("Set-Cookie").isEmpty(), "kein Set-Cookie: " + methode + " " + pfad);
            assertNull(antwort.getHeader("Location"), "nie 302: " + methode + " " + pfad);
        }

        assertEquals(statementsVorher, statistik.getPrepareStatementCount(), "kein SQL ueber Hibernate");
        assertEquals(sessionsVorher, statistik.getSessionOpenCount(), "keine Hibernate-Session");
        assertEquals(reservierungenVorher, reservierungen(), "keine Zeile in ausstehende_registrierung");
    }

    @Test
    void v1OhneAnmeldungIst401AlsJsonNie302() {
        Response antwort = given().redirects().follow(false).get("/v1/info");
        assertEquals(401, antwort.statusCode());
        assertNull(antwort.getHeader("Location"));
        assertEquals("NICHT_ANGEMELDET", antwort.jsonPath().getString("code"));
    }

    @Test
    void v1AnmeldenBleibtErreichbar() {
        given().header("Origin", "http://localhost:8081").post("/v1/anmelden/optionen")
                .then().statusCode(200);
    }

    /** Liest an Hibernate vorbei, damit die Statistik unberuehrt bleibt. */
    private int reservierungen() throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement("SELECT count(*) FROM ausstehende_registrierung");
                ResultSet ergebnis = abfrage.executeQuery()) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
