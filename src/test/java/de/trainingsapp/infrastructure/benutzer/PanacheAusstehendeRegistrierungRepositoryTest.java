package de.trainingsapp.infrastructure.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Ausstehende Registrierungen mit TTL gegen PostgreSQL (V3). */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PanacheAusstehendeRegistrierungRepositoryTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Inject
    AusstehendeRegistrierungRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        assertInstanceOf(PanacheAusstehendeRegistrierungRepository.class, repository);
    }

    @Test
    void reservierenUndEntnehmen() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung neu = neu("Jule", jetzt);

        assertEquals(neu, repository.reserviere(neu, jetzt));
        assertEquals(1, zeilenFuer(neu.benutzername()));

        assertEquals(Optional.of(neu), repository.entnimm(neu.benutzername(), jetzt.plusSeconds(1)));
        assertEquals(0, zeilenFuer(neu.benutzername()));
        assertEquals(Optional.empty(), repository.entnimm(neu.benutzername(), jetzt.plusSeconds(1)));
    }

    @Test
    void gueltigeReservierungBleibtBeiErneutemVersuchErhalten() {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung erste = neu("Kai", jetzt);
        repository.reserviere(erste, jetzt);

        AusstehendeRegistrierung zweiterVersuch = new AusstehendeRegistrierung(BenutzerId.neu(),
                erste.benutzername().toUpperCase(), jetzt.plus(Duration.ofMinutes(9)).plus(TTL));
        AusstehendeRegistrierung wirksam = repository.reserviere(zweiterVersuch, jetzt.plus(Duration.ofMinutes(9)));

        assertEquals(erste, wirksam, "gleiche BenutzerId (User-Handle) und unveraenderte Ablaufzeit");
        assertEquals(Optional.of(erste), repository.entnimm(erste.benutzername().toLowerCase(), jetzt.plusSeconds(1)));
    }

    @Test
    void abgelaufeneReservierungWirdErsetzt() {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung alt = neu("Lea", jetzt);
        repository.reserviere(alt, jetzt);

        Instant spaeter = alt.gueltigBis();
        AusstehendeRegistrierung neu = new AusstehendeRegistrierung(BenutzerId.neu(), alt.benutzername(),
                spaeter.plus(TTL));
        AusstehendeRegistrierung wirksam = repository.reserviere(neu, spaeter);

        assertEquals(neu, wirksam);
        assertNotEquals(alt.vorgesehen(), wirksam.vorgesehen());
    }

    @Test
    void abgelaufeneReservierungWirdBeimEntnehmenEntferntAberNichtGeliefert() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung r = neu("Mia", jetzt);
        repository.reserviere(r, jetzt);

        assertEquals(Optional.empty(), repository.entnimm(r.benutzername(), r.gueltigBis()));
        assertEquals(0, zeilenFuer(r.benutzername()));
    }

    @Test
    void reservierenRaeumtAbgelaufeneAndererNamenAuf() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung verwaist = neu("Nils", jetzt);
        repository.reserviere(verwaist, jetzt);
        assertEquals(1, zeilenFuer(verwaist.benutzername()));

        Instant spaeter = jetzt.plus(TTL).plusSeconds(1);
        repository.reserviere(neu("Ole", spaeter), spaeter);

        assertEquals(0, zeilenFuer(verwaist.benutzername()));
    }

    private static AusstehendeRegistrierung neu(String praefix, Instant jetzt) {
        return new AusstehendeRegistrierung(BenutzerId.neu(), praefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                jetzt.plus(TTL));
    }

    /** Liest per JDBC an Hibernate vorbei. */
    private int zeilenFuer(String benutzername) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement(
                        "SELECT count(*) FROM ausstehende_registrierung WHERE lower(benutzername) = lower(?)")) {
            abfrage.setString(1, benutzername);
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }
}
