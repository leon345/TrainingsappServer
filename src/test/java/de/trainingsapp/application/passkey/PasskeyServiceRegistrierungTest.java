package de.trainingsapp.application.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.application.DirekteTransaktion;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.benutzer.UngueltigerBenutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Registrierung mit ausstehender Reservierung und TTL (E18), ohne Datenbank und ohne Extension:
 * Ports durch einfache Testdoubles ersetzt.
 */
class PasskeyServiceRegistrierungTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StellbareUhr uhr = new StellbareUhr(Instant.parse("2026-11-18T10:00:00Z"));
    private final Benutzerablage benutzer = new Benutzerablage();
    private final Reservierungen ausstehende = new Reservierungen();
    private final Zeremonie zeremonie = new Zeremonie();
    private final Passkeyablage passkeyablage = new Passkeyablage();
    private final List<BenutzerId> gestarteteSitzungen = new ArrayList<>();
    private PasskeyService service;

    @BeforeEach
    void setUp() {
        Anmeldesitzung sitzung = new Anmeldesitzung() {
            @Override
            public void starte(BenutzerId benutzerId) {
                gestarteteSitzungen.add(benutzerId);
            }

            @Override
            public void beende() {
            }
        };
        service = new PasskeyService(benutzer, passkeyablage, ausstehende, zeremonie, sitzung,
                new DirekteTransaktion(), TTL,
                Set.of(new Benutzername("admin")), uhr);
    }

    @Test
    void abschlussInnerhalbDerTtlLegtBenutzerMitReservierterIdAn() {
        service.registrierungStarten("anna");
        BenutzerId vorgesehen = zeremonie.letzterBenutzer.id();

        uhr.vor(TTL.minusSeconds(1));
        BenutzerId angelegt = service.registrierungAbschliessen("anna", "{}");

        assertEquals(vorgesehen, angelegt);
        assertEquals(Optional.of(new Benutzer(vorgesehen, new Benutzername("anna"))), benutzer.findeNachId(vorgesehen));
        assertEquals(List.of(vorgesehen), gestarteteSitzungen);
        assertTrue(ausstehende.eintraege.isEmpty(), "Reservierung nach Abschluss entfernt");
    }

    @Test
    void erneuterStartInnerhalbDerTtlBehaeltDenUserHandle() {
        service.registrierungStarten("anna");
        BenutzerId erster = zeremonie.letzterBenutzer.id();

        uhr.vor(Duration.ofMinutes(5));
        service.registrierungStarten("anna");

        assertEquals(erster, zeremonie.letzterBenutzer.id());
    }

    @Test
    void nachAblaufDerTtlGibtEsEinenNeuenUserHandle() {
        service.registrierungStarten("anna");
        BenutzerId erster = zeremonie.letzterBenutzer.id();

        uhr.vor(TTL);
        service.registrierungStarten("anna");

        assertNotEquals(erster, zeremonie.letzterBenutzer.id());
    }

    @Test
    void abschlussNachAblaufDerTtlWirdAbgelehnt() {
        service.registrierungStarten("anna");

        uhr.vor(TTL);

        assertThrows(PasskeyPruefungFehlgeschlagen.class, () -> service.registrierungAbschliessen("anna", "{}"));
        assertEquals(Optional.empty(), benutzer.findeNachName(new Benutzername("anna")));
        assertEquals(List.of(), gestarteteSitzungen);
    }

    @Test
    void abschlussOhneStartWirdAbgelehnt() {
        assertThrows(PasskeyPruefungFehlgeschlagen.class, () -> service.registrierungAbschliessen("anna", "{}"));
        assertEquals(List.of(), gestarteteSitzungen);
    }

    @Test
    void ungueltigerNameReserviertNichts() {
        assertThrows(UngueltigerBenutzername.class, () -> service.registrierungStarten("anna@example.org"));
        assertTrue(ausstehende.eintraege.isEmpty());
        assertEquals(null, zeremonie.letzterBenutzer);
    }

    @Test
    void gesperrterNameReserviertNichtsAuchInGrossschreibung() {
        assertThrows(UngueltigerBenutzername.class, () -> service.registrierungStarten("Admin"));
        assertTrue(ausstehende.eintraege.isEmpty());
    }

    @Test
    void nameWirdKleingeschriebenGespeichert() {
        service.registrierungStarten("Anna");
        BenutzerId angelegt = service.registrierungAbschliessen("ANNA", "{}");
        assertEquals(new Benutzername("anna"), benutzer.findeNachId(angelegt).orElseThrow().benutzername());
    }

    @Test
    void vergebenerNameReserviertNichts() {
        benutzer.legeAn(new Benutzer(BenutzerId.neu(), new Benutzername("anna")));
        assertThrows(BenutzernameVergeben.class, () -> service.registrierungStarten("anna"));
        assertTrue(ausstehende.eintraege.isEmpty());
    }

    /** E29: erstellt_am ist der Tag in UTC, unabhaengig von der Zeitzone des Servers. */
    @Test
    void erstPasskeyHeisstPasskey1UndTraegtDenUtcTag() {
        uhr.setze(Instant.parse("2026-11-18T23:30:00Z")); // in Berlin schon der 19.11.
        service.registrierungStarten("anna");
        BenutzerId angelegt = service.registrierungAbschliessen("anna", "{}");

        Passkey passkey = passkeyablage.findeFuer(angelegt).get(0);
        assertEquals("Passkey 1", passkey.bezeichnung().wert());
        assertEquals(java.time.LocalDate.parse("2026-11-18"), passkey.erstelltAm());
        assertEquals(null, passkey.zuletztVerwendetAm());
    }

    @Test
    void ttlMussPositivSein() {
        for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThrows(IllegalArgumentException.class, () -> new PasskeyService(benutzer, new Passkeyablage(),
                    ausstehende, zeremonie, null, new DirekteTransaktion(), ttl, Set.of(), uhr));
        }
    }

    // --- Testdoubles ---

    private static final class StellbareUhr extends Clock {
        private Instant jetzt;

        StellbareUhr(Instant start) {
            this.jetzt = start;
        }

        void vor(Duration dauer) {
            jetzt = jetzt.plus(dauer);
        }

        void setze(Instant zeitpunkt) {
            jetzt = zeitpunkt;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }


    private static final class Benutzerablage implements BenutzerRepository {
        final Map<Benutzername, Benutzer> nachName = new HashMap<>();

        @Override
        public Optional<Benutzer> findeNachId(BenutzerId id) {
            return nachName.values().stream().filter(b -> b.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Benutzer> findeNachName(Benutzername name) {
            return Optional.ofNullable(nachName.get(name));
        }

        @Override
        public boolean legeAn(Benutzer b) {
            return nachName.putIfAbsent(b.benutzername(), b) == null;
        }

        final Map<BenutzerId, Integer> nummern = new HashMap<>();

        @Override
        public int naechstePasskeyNummer(BenutzerId id) {
            return nummern.merge(id, 1, Integer::sum);
        }
    }

    private static final class Reservierungen implements AusstehendeRegistrierungRepository {
        final Map<Benutzername, AusstehendeRegistrierung> eintraege = new HashMap<>();

        @Override
        public AusstehendeRegistrierung reserviere(AusstehendeRegistrierung neu, Instant jetzt) {
            eintraege.values().removeIf(r -> !r.istGueltigUm(jetzt));
            return eintraege.computeIfAbsent(neu.benutzername(), k -> neu);
        }

        @Override
        public Optional<AusstehendeRegistrierung> entnimm(Benutzername name, Instant jetzt) {
            return Optional.ofNullable(eintraege.remove(name)).filter(r -> r.istGueltigUm(jetzt));
        }
    }

    private static final class Passkeyablage implements PasskeyRepository {
        final Map<PasskeyId, Passkey> passkeys = new HashMap<>();

        @Override
        public Optional<Passkey> findeNachId(PasskeyId id) {
            return Optional.ofNullable(passkeys.get(id));
        }

        @Override
        public List<Passkey> findeFuer(BenutzerId benutzerId) {
            return passkeys.values().stream().filter(p -> p.benutzerId().equals(benutzerId)).toList();
        }

        @Override
        public boolean fuegeHinzu(Passkey passkey) {
            return passkeys.putIfAbsent(passkey.id(), passkey) == null;
        }

        @Override
        public void vermerkeAnmeldung(PasskeyId id, long zaehler, java.time.LocalDate tag) {
            passkeys.computeIfPresent(id, (k, p) -> p.mitZaehler(zaehler));
        }

        @Override
        public boolean benenneUm(BenutzerId benutzerId, PasskeyId id,
                                 de.trainingsapp.domain.passkey.PasskeyBezeichnung bezeichnung) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoeschErgebnis loescheWennNichtLetzter(BenutzerId benutzerId, PasskeyId id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Zeremonie implements PasskeyZeremonie {
        Benutzer letzterBenutzer;

        @Override
        public String registrierungsOptionen(Benutzer b, List<PasskeyId> vorhandene) {
            letzterBenutzer = b;
            return "{}";
        }

        @Override
        public NeuerPasskey registrierungPruefen(String antwortJson) {
            return new NeuerPasskey(new PasskeyId(UUID.randomUUID().toString()), new byte[] {1}, -7, 0);
        }

        @Override
        public String anmeldeOptionen() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Passkey anmeldungPruefen(String antwortJson) {
            throw new UnsupportedOperationException();
        }
    }
}
