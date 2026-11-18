package de.trainingsapp.infrastructure.persistenz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.platzhalter.Platzhalter;
import de.trainingsapp.domain.platzhalter.PlatzhalterRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

/**
 * Prueft gegen eine echte PostgreSQL-Datenbank: Flyway wendet V1 beim Start an,
 * Hibernate validiert das Schema, und ein Domain-Objekt kommt ueber Port und
 * Panache-Adapter unveraendert zurueck.
 */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PersistenzGrundlageTest {

    @Inject
    Flyway flyway;

    @Inject
    PlatzhalterRepository repository;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "quarkus.hibernate-orm.schema-management.strategy")
    String schemaManagement;

    @Test
    void laeuftGegenEchtePostgresql() throws SQLException {
        try (Connection verbindung = dataSource.getConnection()) {
            assertEquals("PostgreSQL", verbindung.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void v1MigrationWurdeBeimStartAngewendet() {
        MigrationInfo[] angewendet = flyway.info().applied();
        assertTrue(Arrays.stream(angewendet)
                        .anyMatch(m -> m.getVersion() != null && "1".equals(m.getVersion().getVersion())
                                && m.getState().isApplied() && !m.getState().isFailed()),
                "V1 nicht angewendet");
        assertEquals(0, flyway.info().pending().length, "es gibt nicht angewendete Migrationen");
    }

    @Test
    void hibernateValidiertDasSchemaBeimStart() {
        // Der Start waere mit SchemaManagementException abgebrochen, wenn Tabelle
        // oder Spalten nicht zur Entity passten.
        assertEquals("validate", schemaManagement);
    }

    @Test
    void domainObjektUeberPortSpeichernUndLesen() throws SQLException {
        Platzhalter platzhalter = new Platzhalter(UUID.randomUUID(), "Platzhalter");

        repository.speichern(platzhalter);

        assertEquals(Optional.of("Platzhalter"), nameDirektAusDatenbank(platzhalter.id()),
                "Zeile nicht in der Tabelle platzhalter");
        assertEquals(Optional.of(platzhalter), repository.findeNachId(platzhalter.id()));
        assertEquals(Optional.empty(), repository.findeNachId(UUID.randomUUID()));
    }

    @Test
    void speichernAktualisiertVorhandenesObjekt() throws SQLException {
        UUID id = UUID.randomUUID();
        repository.speichern(new Platzhalter(id, "alt"));

        repository.speichern(new Platzhalter(id, "neu"));

        assertEquals(Optional.of("neu"), nameDirektAusDatenbank(id));
        assertEquals(Optional.of(new Platzhalter(id, "neu")), repository.findeNachId(id));
    }

    /** Liest an Hibernate vorbei per JDBC, damit kein Cache das Ergebnis verfaelscht. */
    private Optional<String> nameDirektAusDatenbank(UUID id) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement("SELECT name FROM platzhalter WHERE id = ?")) {
            abfrage.setObject(1, id);
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                return ergebnis.next() ? Optional.of(ergebnis.getString(1)) : Optional.empty();
            }
        }
    }
}
