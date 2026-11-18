package de.trainingsapp.infrastructure.passkey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.passkey.PasskeyRepository.LoeschErgebnis;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Inject
    PasskeyRepository repository;

    @Inject
    BenutzerRepository benutzer;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        assertInstanceOf(PanachePasskeyRepository.class, repository);
    }

    @Test
    void speichernUndLesenAllerFelder() {
        BenutzerId anna = neuerBenutzer();
        Passkey passkey = passkey(anna, T0, 1023);

        assertTrue(repository.fuegeHinzu(passkey));

        Passkey gelesen = repository.findeNachId(passkey.id()).orElseThrow();
        assertEquals(passkey.id(), gelesen.id());
        assertEquals(anna, gelesen.benutzerId());
        assertEquals(passkey.aaguid(), gelesen.aaguid());
        assertArrayEquals(passkey.publicKey(), gelesen.publicKey());
        assertEquals(-8, gelesen.algorithmus());
        assertEquals(0, gelesen.zaehler());
        assertEquals(T0, gelesen.erstelltAm());
        assertEquals(Optional.empty(), repository.findeNachId(new PasskeyId("unbekannt")));
    }

    @Test
    void aaguidDarfFehlen() {
        BenutzerId id = neuerBenutzer();
        Passkey ohne = new Passkey(credentialId(32), id, null, new byte[] {1, 2, 3}, -7, 5, T0);
        assertTrue(repository.fuegeHinzu(ohne));
        assertEquals(null, repository.findeNachId(ohne.id()).orElseThrow().aaguid());
    }

    @Test
    void credentialIdIstGlobalEindeutig() {
        Passkey passkey = passkey(neuerBenutzer(), T0, 16);
        assertTrue(repository.fuegeHinzu(passkey));
        Passkey gleicheIdAndererBenutzer = new Passkey(passkey.id(), neuerBenutzer(), null, new byte[] {9}, -7, 0, T0);

        assertFalse(repository.fuegeHinzu(gleicheIdAndererBenutzer));
        assertEquals(passkey.benutzerId(), repository.findeNachId(passkey.id()).orElseThrow().benutzerId());
    }

    @Test
    void listeJeBenutzerAeltesteZuerst() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        Passkey spaet = passkey(anna, T0.plus(1, ChronoUnit.HOURS), 16);
        Passkey frueh = passkey(anna, T0, 16);
        Passkey fremd = passkey(ben, T0, 16);
        repository.fuegeHinzu(spaet);
        repository.fuegeHinzu(frueh);
        repository.fuegeHinzu(fremd);

        assertEquals(List.of(frueh.id(), spaet.id()), repository.findeFuer(anna).stream().map(Passkey::id).toList());
        assertEquals(List.of(fremd.id()), repository.findeFuer(ben).stream().map(Passkey::id).toList());
        assertEquals(List.of(), repository.findeFuer(BenutzerId.neu()));
    }

    @Test
    void zaehlerWirdAktualisiert() {
        Passkey passkey = passkey(neuerBenutzer(), T0, 16);
        repository.fuegeHinzu(passkey);

        repository.aktualisiereZaehler(passkey.id(), 42);

        assertEquals(42, repository.findeNachId(passkey.id()).orElseThrow().zaehler());
    }

    @Test
    void loeschenMitSperreFuerDenLetzten() {
        BenutzerId anna = neuerBenutzer();
        BenutzerId ben = neuerBenutzer();
        Passkey a1 = passkey(anna, T0, 16);
        Passkey a2 = passkey(anna, T0.plusSeconds(1), 16);
        Passkey b1 = passkey(ben, T0, 16);
        Passkey b2 = passkey(ben, T0.plusSeconds(1), 16);
        List.of(a1, a2, b1, b2).forEach(repository::fuegeHinzu);

        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, repository.loescheWennNichtLetzter(anna, b1.id()));
        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, repository.loescheWennNichtLetzter(anna, credentialId(16)));
        assertEquals(LoeschErgebnis.NICHT_GEFUNDEN, repository.loescheWennNichtLetzter(BenutzerId.neu(), a1.id()));
        assertEquals(2, repository.findeFuer(ben).size());

        assertEquals(LoeschErgebnis.GELOESCHT, repository.loescheWennNichtLetzter(anna, a1.id()));
        assertEquals(LoeschErgebnis.LETZTER_PASSKEY, repository.loescheWennNichtLetzter(anna, a2.id()));
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
                Passkey p2 = passkey(id, T0.plusSeconds(1), 16);
                repository.fuegeHinzu(p1);
                repository.fuegeHinzu(p2);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<LoeschErgebnis>> ergebnisse = new ArrayList<>();
                for (Passkey p : List.of(p1, p2)) {
                    Callable<LoeschErgebnis> loeschen = () -> {
                        start.await();
                        return repository.loescheWennNichtLetzter(id, p.id());
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
        assertTrue(benutzer.legeAn(new Benutzer(id, "pk-" + id.toString().substring(0, 13))));
        return id;
    }

    private static Passkey passkey(BenutzerId benutzerId, Instant erstelltAm, int credentialLaenge) {
        byte[] schluessel = new byte[91];
        ThreadLocalRandom.current().nextBytes(schluessel);
        return new Passkey(credentialId(credentialLaenge), benutzerId, UUID.randomUUID(), schluessel, -8, 0,
                erstelltAm);
    }

    private static PasskeyId credentialId(int laenge) {
        byte[] roh = new byte[laenge];
        ThreadLocalRandom.current().nextBytes(roh);
        return new PasskeyId(Base64.getUrlEncoder().withoutPadding().encodeToString(roh));
    }
}
