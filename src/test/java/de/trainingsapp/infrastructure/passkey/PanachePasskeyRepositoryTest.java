package de.trainingsapp.infrastructure.passkey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.application.Transaktion;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.passkey.PasskeyRepository.LoeschErgebnis;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/** Datenbank-Adapter fuer Passkeys gegen PostgreSQL (V2), inklusive Loeschsperre. */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PanachePasskeyRepositoryTest {

    private static final LocalDate T0 = LocalDate.parse("2026-11-18");

    @Inject
    PasskeyRepository repository;

    @Inject
    BenutzerRepository benutzer;

    @Inject
    DataSource dataSource;

    @Inject
    Transaktion transaktion;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        assertInstanceOf(PanachePasskeyRepository.class, repository);
    }

    @Test
    void speichernUndLesenAllerFelder() {
        BenutzerId anna = neuerBenutzer();
        Passkey passkey = passkey(anna, T0, 1023);

        assertTrue(transaktion.ausfuehren(() -> repository.fuegeHinzu(passkey)));

        Passkey gelesen = repository.findeNachId(passkey.id()).orElseThrow();
        assertEquals(passkey.id(), gelesen.id());
        assertEquals(anna, gelesen.benutzerId());
        assertEquals(new PasskeyBezeichnung("Passkey 1"), gelesen.bezeichnung());
        assertArrayEquals(passkey.publicKey(), gelesen.publicKey());
        assertEquals(-8, gelesen.algorithmus());
        assertEquals(0, gelesen.zaehler());
        assertEquals(T0, gelesen.erstelltAm());
        assertEquals(null, gelesen.zuletztVerwendetAm());
        assertEquals(Optional.empty(), repository.findeNachId(new PasskeyId("unbekannt")));
    }

    @Test
    void credentialIdIstGlobalEindeutig() {
        Passkey passkey = passkey(neuerBenutzer(), T0, 16);
        assertTrue(transaktion.ausfuehren(() -> repository.fuegeHinzu(passkey)));
        Passkey gleicheIdAndererBenutzer = new Passkey(passkey.id(), neuerBenutzer(),
                PasskeyBezeichnung.automatisch(1), new byte[] {9}, -7, 0, T0, null);

        assertFalse(transaktion.ausfuehren(() -> repository.fuegeHinzu(gleicheIdAndererBenutzer)));
        assertEquals(passkey.benutzerId(), repository.findeNachId(passkey.id()).orElseThrow().benutzerId());
    }

    @Test
    void listeJeBenutzerAeltesteZuerst() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        Passkey spaet = passkey(anna, T0.plusDays(1), 16);
        Passkey frueh = passkey(anna, T0, 16);
        Passkey fremd = passkey(ben, T0, 16);
        transaktion.ausfuehren(() -> repository.fuegeHinzu(spaet));
        transaktion.ausfuehren(() -> repository.fuegeHinzu(frueh));
        transaktion.ausfuehren(() -> repository.fuegeHinzu(fremd));

        assertEquals(List.of(frueh.id(), spaet.id()), repository.findeFuer(anna).stream().map(Passkey::id).toList());
        assertEquals(List.of(fremd.id()), repository.findeFuer(ben).stream().map(Passkey::id).toList());
        assertEquals(List.of(), repository.findeFuer(BenutzerId.neu()));
    }

    @Test
    void zaehlerWirdAktualisiert() {
        Passkey passkey = passkey(neuerBenutzer(), T0, 16);
        transaktion.ausfuehren(() -> repository.fuegeHinzu(passkey));

        transaktion.ausfuehren(() -> repository.vermerkeAnmeldung(passkey.id(), 42, T0));

        assertEquals(42, repository.findeNachId(passkey.id()).orElseThrow().zaehler());
    }

    /**
     * E29: zuletzt_verwendet_am hoechstens einmal pro Tag. Der Tag kommt von der (hier kontrollierten)
     * Uhr des Aufrufers. Beleg ueber die PostgreSQL-Systemspalte xmin, die sich bei jedem Schreiben
     * der Zeile aendert (Zaehler 0 wie bei synchronisierten Passkeys, damit nur das Datum zaehlt).
     */
    @Test
    void zuletztVerwendetAmWirdHoechstensEinmalProTagGeschrieben() throws SQLException {
        Passkey passkey = passkey(neuerBenutzer(), T0, 16);
        transaktion.ausfuehren(() -> repository.fuegeHinzu(passkey));
        LocalDate tag1 = T0.plusDays(3);
        long vorher = xmin(passkey.id());

        transaktion.ausfuehren(() -> repository.vermerkeAnmeldung(passkey.id(), 0, tag1));
        long nachErsterAnmeldung = xmin(passkey.id());
        assertNotEquals(vorher, nachErsterAnmeldung, "erste Anmeldung des Tages schreibt");
        assertEquals(tag1, repository.findeNachId(passkey.id()).orElseThrow().zuletztVerwendetAm());

        transaktion.ausfuehren(() -> repository.vermerkeAnmeldung(passkey.id(), 0, tag1));
        transaktion.ausfuehren(() -> repository.vermerkeAnmeldung(passkey.id(), 0, tag1));
        assertEquals(nachErsterAnmeldung, xmin(passkey.id()), "weitere Anmeldungen am selben Tag schreiben nicht");

        LocalDate tag2 = tag1.plusDays(1);
        transaktion.ausfuehren(() -> repository.vermerkeAnmeldung(passkey.id(), 0, tag2));
        assertNotEquals(nachErsterAnmeldung, xmin(passkey.id()), "neuer Tag schreibt");
        assertEquals(tag2, repository.findeNachId(passkey.id()).orElseThrow().zuletztVerwendetAm());
    }

    @Test
    void umbenennenNurFuerDenBesitzer() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        Passkey passkey = passkey(anna, T0, 16);
        transaktion.ausfuehren(() -> repository.fuegeHinzu(passkey));
        PasskeyBezeichnung neu = PasskeyBezeichnung.aus("Mein Schlüssel");

        assertFalse(transaktion.ausfuehren(() -> repository.benenneUm(ben, passkey.id(), neu)));
        assertEquals(PasskeyBezeichnung.automatisch(1), repository.findeNachId(passkey.id()).orElseThrow().bezeichnung());
        assertFalse(transaktion.ausfuehren(() -> repository.benenneUm(anna, credentialId(16), neu)));

        assertTrue(transaktion.ausfuehren(() -> repository.benenneUm(anna, passkey.id(), neu)));
        assertEquals(neu, repository.findeNachId(passkey.id()).orElseThrow().bezeichnung());
    }

    @Test
    void passkeyNummernSteigenUndWerdenNichtWiederverwendet() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        assertEquals(1, transaktion.ausfuehren(() -> benutzer.naechstePasskeyNummer(anna)));
        assertEquals(2, transaktion.ausfuehren(() -> benutzer.naechstePasskeyNummer(anna)));
        assertEquals(1, transaktion.ausfuehren(() -> benutzer.naechstePasskeyNummer(ben)), "je Benutzer");
        // Ein Rollback gibt die Nummer frei
        assertThrows(IllegalStateException.class, () -> transaktion.ausfuehren(() -> {
            benutzer.naechstePasskeyNummer(anna);
            throw new IllegalStateException("Abbruch");
        }));
        assertEquals(3, transaktion.ausfuehren(() -> benutzer.naechstePasskeyNummer(anna)));
    }

    @Test
    void loeschenMitSperreFuerDenLetzten() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        Passkey a1 = passkey(anna, T0, 16);
        Passkey a2 = passkey(anna, T0.plusDays(1), 16);
        Passkey b1 = passkey(ben, T0, 16);
        Passkey b2 = passkey(ben, T0.plusDays(1), 16);
        List.of(a1, a2, b1, b2).forEach(p -> transaktion.ausfuehren(() -> repository.fuegeHinzu(p)));

        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(anna, b1.id())));
        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(anna, credentialId(16))));
        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(BenutzerId.neu(), a1.id())));
        assertEquals(2, repository.findeFuer(ben).size());

        assertEquals(LoeschErgebnis.GELOESCHT, transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(anna, a1.id())));
        assertEquals(LoeschErgebnis.LETZTER_PASSKEY, transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(anna, a2.id())));
        assertEquals(List.of(a2.id()), repository.findeFuer(anna).stream().map(Passkey::id).toList());
        assertEquals(Optional.empty(), repository.findeNachId(a1.id()));
    }

    @Test
    void paralleleLoeschungenEntfernenNieDenLetzten() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int runde = 0; runde < 10; runde++) {
                BenutzerId id = neuerBenutzer();
                Passkey p1 = passkey(id, T0, 16);
                Passkey p2 = passkey(id, T0.plusDays(1), 16);
                transaktion.ausfuehren(() -> repository.fuegeHinzu(p1));
                transaktion.ausfuehren(() -> repository.fuegeHinzu(p2));
                CountDownLatch start = new CountDownLatch(1);
                List<Future<LoeschErgebnis>> ergebnisse = new ArrayList<>();
                for (Passkey p : List.of(p1, p2)) {
                    Callable<LoeschErgebnis> loeschen = () -> {
                        start.await();
                        return transaktion.ausfuehren(() -> repository.loescheWennNichtLetzter(id, p.id()));
                    };
                    ergebnisse.add(pool.submit(loeschen));
                }
                start.countDown();
                List<LoeschErgebnis> beide = List.of(ergebnisse.get(0).get(), ergebnisse.get(1).get());

                assertTrue(beide.contains(LoeschErgebnis.GELOESCHT), beide.toString());
                assertTrue(beide.contains(LoeschErgebnis.LETZTER_PASSKEY), beide.toString());
                assertEquals(1, repository.findeFuer(id).size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private BenutzerId neuerBenutzer() {
        BenutzerId id = BenutzerId.neu();
        assertTrue(transaktion.ausfuehren(() -> benutzer.legeAn(new Benutzer(id, new Benutzername("pk-" + id.toString().substring(0, 13))))));
        return id;
    }

    private static Passkey passkey(BenutzerId benutzerId, LocalDate erstelltAm, int credentialLaenge) {
        byte[] schluessel = new byte[91];
        ThreadLocalRandom.current().nextBytes(schluessel);
        return new Passkey(credentialId(credentialLaenge), benutzerId, PasskeyBezeichnung.automatisch(1), schluessel,
                -8, 0, erstelltAm, null);
    }

    private long xmin(PasskeyId id) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement abfrage = verbindung.prepareStatement(
                        "SELECT xmin::text::bigint FROM passkey WHERE credential_id = ?")) {
            abfrage.setString(1, id.wert());
            try (ResultSet ergebnis = abfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getLong(1);
            }
        }
    }

    private static PasskeyId credentialId(int laenge) {
        byte[] roh = new byte[laenge];
        ThreadLocalRandom.current().nextBytes(roh);
        return new PasskeyId(Base64.getUrlEncoder().withoutPadding().encodeToString(roh));
    }
}
