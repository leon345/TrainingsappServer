package de.trainingsapp.infrastructure.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Datenbank-Adapter fuer Benutzer gegen PostgreSQL (V2). */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PanacheBenutzerRepositoryTest {

    @Inject
    BenutzerRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        // Kein In-Memory-Adapter mehr: der Port wird ausschliesslich von der Datenbank umgesetzt.
        assertInstanceOf(PanacheBenutzerRepository.class, repository);
    }

    @Test
    void anlegenUndLesenNachIdUndName() {
        Benutzer benutzer = new Benutzer(BenutzerId.neu(), eindeutig("greta"));

        assertTrue(repository.legeAn(benutzer));

        assertEquals(Optional.of(benutzer), repository.findeNachId(benutzer.id()));
        assertEquals(Optional.of(benutzer), repository.findeNachName(benutzer.benutzername()));
        assertEquals(Optional.empty(), repository.findeNachId(BenutzerId.neu()));
        assertEquals(Optional.empty(), repository.findeNachName(eindeutig("niemand")));
    }

    @Test
    void nameIstEindeutig() {
        Benutzername name = eindeutig("hanna");
        Benutzer erster = new Benutzer(BenutzerId.neu(), name);
        assertTrue(repository.legeAn(erster));

        assertFalse(repository.legeAn(new Benutzer(BenutzerId.neu(), name)));
        assertEquals(Optional.of(erster), repository.findeNachName(name));
    }

    @Test
    void gleicheIdWirdNichtZweimalAngelegt() {
        BenutzerId id = BenutzerId.neu();
        assertTrue(repository.legeAn(new Benutzer(id, eindeutig("ida"))));
        assertFalse(repository.legeAn(new Benutzer(id, eindeutig("ida"))));
    }

    /** E28: Die Spalte hat einen CHECK mit derselben Regex wie das Value Object. */
    @ParameterizedTest
    @ValueSource(strings = {"Anna", "ab", "a..b", ".abc", "abc-", "jürgen", "an na",
            "abcdefghijabcdefghijabcdefghijabc", "anna@example.org"})
    void checkConstraintLehntUngueltigeNamenAb(String name) {
        SQLException fehler = assertThrows(SQLException.class, () -> direktEinfuegen(name));
        // 23514 check_violation; bei 33 Zeichen greift vorher varchar(32) mit 22001
        assertTrue(java.util.Set.of("23514", "22001").contains(fehler.getSQLState()), fehler.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a.b", "lauf_team.2026", "abcdefghijabcdefghijabcdefghijab"})
    void checkConstraintNimmtGueltigeNamenAn(String name) throws SQLException {
        direktEinfuegen(name + (name.length() < 26 ? "-" + UUID.randomUUID().toString().substring(0, 4) : ""));
    }

    /** Java-Regex und PostgreSQL-Regex muessen fuer dieselben Werte gleich entscheiden. */
    @ParameterizedTest
    @ValueSource(strings = {"abc", "ab", "a.b", "a..b", "a._b", ".ab", "ab.", "abcdefghijabcdefghijabcdefghijab",
            "abcdefghijabcdefghijabcdefghijabc", "a-b-c", "007", "Abc", "jürgen", "a b"})
    void javaUndPostgresqlEntscheidenGleich(String wert) throws SQLException {
        boolean java = wert.matches(Benutzername.REGEX);
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement("SELECT ? ~ ?")) {
            abfrage.setString(1, wert);
            abfrage.setString(2, Benutzername.REGEX);
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                ergebnis.next();
                assertEquals(java, ergebnis.getBoolean(1), wert);
            }
        }
    }

    private void direktEinfuegen(String name) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement einfuegen = verbindung.prepareStatement(
                        "INSERT INTO benutzer (id, benutzername) VALUES (?, ?)")) {
            einfuegen.setObject(1, UUID.randomUUID());
            einfuegen.setString(2, name);
            einfuegen.executeUpdate();
        }
    }

    private static Benutzername eindeutig(String praefix) {
        return new Benutzername(praefix + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
