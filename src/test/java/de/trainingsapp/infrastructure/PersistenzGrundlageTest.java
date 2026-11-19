package de.trainingsapp.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PersistenzGrundlageTest {

    @Inject
    DataSource dataSource;

    @Inject
    TransaktionsTestHelfer helfer;

    @Test
    void flywayV1WurdeGegenPostgreSqlAusgefuehrt() throws SQLException {
        try (Connection verbindung = dataSource.getConnection()) {
            String produkt = verbindung.getMetaData().getDatabaseProductName();
            assertTrue(produkt.toLowerCase().contains("postgresql"), "Erwartet PostgreSQL, erhalten: " + produkt);

            try (PreparedStatement stmt = verbindung.prepareStatement(
                    "SELECT success FROM flyway_schema_history WHERE version = '1'");
                 ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Flyway-Migration V1 fehlt in flyway_schema_history");
                assertTrue(rs.getBoolean("success"), "Flyway-Migration V1 war nicht erfolgreich");
            }
        }

        assertEquals(Optional.of("1"), helfer.leseWert("schema_version"));
    }

    @Test
    void transactionalCommitUndRollbackFunktionieren() {
        String commitKey = "test-commit-" + UUID.randomUUID();
        String rollbackKey = "test-rollback-" + UUID.randomUUID();

        try {
            helfer.speichereMitCommit(commitKey, "ok");
            assertEquals(Optional.of("ok"), helfer.leseWert(commitKey));

            assertThrows(IllegalStateException.class, () -> helfer.speichereUndWerfeFehler(rollbackKey, "soll-zurueckgerollt-werden"));
            assertFalse(helfer.leseWert(rollbackKey).isPresent(), "Nach RuntimeException muss @Transactional ein Rollback ausfuehren");
        } finally {
            helfer.loesche(commitKey);
        }
    }

    @ApplicationScoped
    static class TransaktionsTestHelfer {

        @Inject
        DataSource dataSource;

        @Transactional
        void speichereMitCommit(String schluessel, String wert) {
            einfuegen(schluessel, wert);
        }

        @Transactional
        void speichereUndWerfeFehler(String schluessel, String wert) {
            einfuegen(schluessel, wert);
            throw new IllegalStateException("Absichtlicher Fehler fuer Rollback-Test");
        }

        @Transactional
        void loesche(String schluessel) {
            try (Connection verbindung = dataSource.getConnection();
                 PreparedStatement stmt = verbindung.prepareStatement(
                         "DELETE FROM schema_meta WHERE schluessel = ?")) {
                stmt.setString(1, schluessel);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        Optional<String> leseWert(String schluessel) {
            try (Connection verbindung = dataSource.getConnection();
                 PreparedStatement stmt = verbindung.prepareStatement(
                         "SELECT wert FROM schema_meta WHERE schluessel = ?")) {
                stmt.setString(1, schluessel);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString("wert")) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private void einfuegen(String schluessel, String wert) {
            try (Connection verbindung = dataSource.getConnection();
                 PreparedStatement stmt = verbindung.prepareStatement(
                         "INSERT INTO schema_meta (schluessel, wert) VALUES (?, ?)")) {
                stmt.setString(1, schluessel);
                stmt.setString(2, wert);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
