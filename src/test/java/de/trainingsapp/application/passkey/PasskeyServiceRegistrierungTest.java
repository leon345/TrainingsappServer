package de.trainingsapp.application.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        service = new PasskeyService(benutzer, new Passkeyablage(), ausstehende, zeremonie, sitzung, TTL, uhr);
    }

    @Test
    void abschlussInnerhalbDerTtlLegtBenutzerMitReservierterIdAn() {
        service.registrierungStarten("anna");
        BenutzerId vorgesehen = zeremonie.letzterBenutzer.id();

        uhr.vor(TTL.minusSeconds(1));
        BenutzerId angelegt = service.registrierungAbschliessen("anna", "{}");

        assertEquals(vorgesehen, angelegt);
        assertEquals(Optional.of(new Benutzer(vorgesehen, "anna")), benutzer.findeNachId(vorgesehen));
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
        assertEquals(Optional.empty(), benutzer.findeNachName("anna"));
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
    void vergebenerNameReserviertNichts() {
        benutzer.legeAn(new Benutzer(BenutzerId.neu(), "anna"));
        assertThrows(BenutzernameVergeben.class, () -> service.registrierungStarten("anna"));
        assertTrue(ausstehende.eintraege.isEmpty());
    }

    @Test
    void ttlMussPositivSein() {
        for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThrows(IllegalArgumentException.class, () -> new PasskeyService(benutzer, new Passkeyablage(),
                    ausstehende, zeremonie, null, ttl, uhr));
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

    private static String schluessel(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class Benutzerablage implements BenutzerRepository {
        final Map<String, Benutzer> nachName = new HashMap<>();

        @Override
        public Optional<Benutzer> findeNachId(BenutzerId id) {
            return nachName.values().stream().filter(b -> b.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Benutzer> findeNachName(String name) {
            return Optional.ofNullable(nachName.get(schluessel(name)));
        }

        @Override
        public boolean legeAn(Benutzer b) {
            return nachName.putIfAbsent(schluessel(b.benutzername()), b) == null;
        }
    }

    private static final class Reservierungen implements AusstehendeRegistrierungRepository {
        final Map<String, AusstehendeRegistrierung> eintraege = new HashMap<>();

        @Override
        public AusstehendeRegistrierung reserviere(AusstehendeRegistrierung neu, Instant jetzt) {
            eintraege.values().removeIf(r -> !r.istGueltigUm(jetzt));
            return eintraege.computeIfAbsent(schluessel(neu.benutzername()), k -> neu);
        }

        @Override
        public Optional<AusstehendeRegistrierung> entnimm(String name, Instant jetzt) {
            return Optional.ofNullable(eintraege.remove(schluessel(name))).filter(r -> r.istGueltigUm(jetzt));
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
        public void aktualisiereZaehler(PasskeyId id, long zaehler) {
            passkeys.computeIfPresent(id, (k, p) -> p.mitZaehler(zaehler));
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
            return new NeuerPasskey(new PasskeyId(UUID.randomUUID().toString()), null, new byte[] {1}, -7, 0);
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
