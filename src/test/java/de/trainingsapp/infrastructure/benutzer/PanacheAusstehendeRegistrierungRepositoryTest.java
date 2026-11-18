package de.trainingsapp.infrastructure.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.trainingsapp.application.Transaktion;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.BenutzerId;
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
    private static final String CH = "challenge-1";

    @Inject
    AusstehendeRegistrierungRepository repository;

    @Inject
    DataSource dataSource;

    @Inject
    Transaktion transaktion;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        assertInstanceOf(PanacheAusstehendeRegistrierungRepository.class, repository);
    }

    @Test
    void reservierenUndEntnehmen() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung neu = neu("Jule", jetzt);

        assertEquals(neu, transaktion.ausfuehren(() -> repository.reserviere(neu, jetzt)));
        transaktion.ausfuehren(() -> repository.bindeChallenge(neu.vorgesehen(), CH));
        assertEquals(1, zeilenFuer(neu.benutzername()));

        assertEquals(Optional.of(neu), transaktion.ausfuehren(() -> repository.entnimm(neu.benutzername(), CH, jetzt.plusSeconds(1))));
        assertEquals(0, zeilenFuer(neu.benutzername()));
        assertEquals(Optional.empty(), transaktion.ausfuehren(() -> repository.entnimm(neu.benutzername(), CH, jetzt.plusSeconds(1))));
    }

    @Test
    void gueltigeReservierungBleibtBeiErneutemVersuchErhalten() {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung erste = neu("Kai", jetzt);
        transaktion.ausfuehren(() -> repository.reserviere(erste, jetzt));
        transaktion.ausfuehren(() -> repository.bindeChallenge(erste.vorgesehen(), CH));

        AusstehendeRegistrierung zweiterVersuch = new AusstehendeRegistrierung(BenutzerId.neu(),
                erste.benutzername(), jetzt.plus(Duration.ofMinutes(9)).plus(TTL));
        AusstehendeRegistrierung wirksam = transaktion.ausfuehren(() -> repository.reserviere(zweiterVersuch, jetzt.plus(Duration.ofMinutes(9))));

        assertEquals(erste, wirksam, "gleiche BenutzerId (User-Handle) und unveraenderte Ablaufzeit");
        assertEquals(Optional.of(erste), transaktion.ausfuehren(() -> repository.entnimm(erste.benutzername(), CH, jetzt.plusSeconds(1))));
    }

    @Test
    void abgelaufeneReservierungWirdErsetzt() {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung alt = neu("Lea", jetzt);
        transaktion.ausfuehren(() -> repository.reserviere(alt, jetzt));

        Instant spaeter = alt.gueltigBis();
        AusstehendeRegistrierung neu = new AusstehendeRegistrierung(BenutzerId.neu(), alt.benutzername(),
                spaeter.plus(TTL));
        AusstehendeRegistrierung wirksam = transaktion.ausfuehren(() -> repository.reserviere(neu, spaeter));

        assertEquals(neu, wirksam);
        assertNotEquals(alt.vorgesehen(), wirksam.vorgesehen());
    }

    @Test
    void abgelaufeneReservierungWirdBeimEntnehmenEntferntAberNichtGeliefert() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung r = neu("Mia", jetzt);
        transaktion.ausfuehren(() -> repository.reserviere(r, jetzt));
        transaktion.ausfuehren(() -> repository.bindeChallenge(r.vorgesehen(), CH));

        assertEquals(Optional.empty(), transaktion.ausfuehren(() -> repository.entnimm(r.benutzername(), CH, r.gueltigBis())));
        assertEquals(0, zeilenFuer(r.benutzername()));
    }

    @Test
    void reservierenRaeumtAbgelaufeneAndererNamenAuf() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung verwaist = neu("Nils", jetzt);
        transaktion.ausfuehren(() -> repository.reserviere(verwaist, jetzt));
        assertEquals(1, zeilenFuer(verwaist.benutzername()));

        Instant spaeter = jetzt.plus(TTL).plusSeconds(1);
        transaktion.ausfuehren(() -> repository.reserviere(neu("Ole", spaeter), spaeter));

        assertEquals(0, zeilenFuer(verwaist.benutzername()));
    }

    /** B1: Nur die gebundene Challenge schliesst ab; eine andere laesst die Reservierung unveraendert. */
    @Test
    void entnehmenNurMitDerGebundenenChallenge() throws SQLException {
        Instant jetzt = Instant.parse("2100-01-01T10:00:00Z");
        AusstehendeRegistrierung r = neu("Pia", jetzt);
        transaktion.ausfuehren(() -> repository.reserviere(r, jetzt));

        assertEquals(Optional.empty(), transaktion.ausfuehren(() -> repository.entnimm(r.benutzername(), CH, jetzt)),
                "ohne gebundene Challenge");
        transaktion.ausfuehren(() -> repository.bindeChallenge(r.vorgesehen(), CH));
        assertEquals(Optional.empty(),
                transaktion.ausfuehren(() -> repository.entnimm(r.benutzername(), "andere", jetzt)));
        assertEquals(1, zeilenFuer(r.benutzername()), "Reservierung bleibt bei falscher Challenge");

        transaktion.ausfuehren(() -> repository.bindeChallenge(r.vorgesehen(), "neuere"));
        assertEquals(Optional.empty(), transaktion.ausfuehren(() -> repository.entnimm(r.benutzername(), CH, jetzt)),
                "fruehere Challenge ist ersetzt");
        assertEquals(Optional.of(r), transaktion.ausfuehren(() -> repository.entnimm(r.benutzername(), "neuere", jetzt)));
    }

    private static AusstehendeRegistrierung neu(String praefix, Instant jetzt) {
        return new AusstehendeRegistrierung(BenutzerId.neu(),
                Benutzername.aus(praefix + "-" + UUID.randomUUID().toString().substring(0, 8)),
                jetzt.plus(TTL));
    }

    /** Liest per JDBC an Hibernate vorbei. */
    private int zeilenFuer(Benutzername benutzername) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement(
                        "SELECT count(*) FROM ausstehende_registrierung WHERE benutzername = ?")) {
            abfrage.setString(1, benutzername.wert());
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }
}
