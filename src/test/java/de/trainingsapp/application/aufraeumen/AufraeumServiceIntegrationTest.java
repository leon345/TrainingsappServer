package de.trainingsapp.application.aufraeumen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.infrastructure.zeit.SystemUhr;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AufraeumServiceIntegrationTest {

    @Inject
    AufraeumService aufraeumService;

    @Inject
    AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository;

    @Inject
    BenutzerRepository benutzerRepository;

    @Inject
    PasskeyRepository passkeyRepository;

    @AfterEach
    void uhrZuruecksetzen() {
        QuarkusMock.installMockForType(new SystemUhr(), Uhr.class);
    }

    @Test
    void loeschtNurAbgelaufeneRegistrierungenUndKontenOhnePasskeyAelterAls14Tage() {
        Instant stichtagJetzt = Instant.parse("2026-11-19T03:15:00Z");
        QuarkusMock.installMockForType(new SystemUhr() {
            @Override
            public Instant jetzt() {
                return stichtagJetzt;
            }
        }, Uhr.class);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Benutzername regAbgelaufenName = new Benutzername("reg.abg." + suffix);
        Benutzername regAktivName = new Benutzername("reg.akt." + suffix);
        BenutzerId konto15TageOhnePasskeyId = BenutzerId.neu();
        BenutzerId konto13TageOhnePasskeyId = BenutzerId.neu();
        BenutzerId konto30TageMitPasskeyId = BenutzerId.neu();

        testDatenAnlegen(
                stichtagJetzt,
                suffix,
                regAbgelaufenName,
                regAktivName,
                konto15TageOhnePasskeyId,
                konto13TageOhnePasskeyId,
                konto30TageMitPasskeyId
        );

        AufraeumErgebnis ergebnis = aufraeumService.aufraeumen();

        assertTrue(ergebnis.geloeschteRegistrierungen() >= 1);
        assertTrue(ergebnis.geloeschteKontenOhnePasskey() >= 1);

        // Abgelaufene Registrierung wurde gelöscht; aktive Registrierung bleibt erhalten
        assertTrue(ausstehendeRegistrierungRepository.findeNachBenutzername(regAbgelaufenName).isEmpty());
        assertTrue(ausstehendeRegistrierungRepository.findeNachBenutzername(regAktivName).isPresent());

        // 15 Tage altes Konto ohne Passkey wurde gelöscht
        assertTrue(benutzerRepository.findeNachId(konto15TageOhnePasskeyId).isEmpty());

        // 13 Tage altes Konto ohne Passkey bleibt erhalten
        assertTrue(benutzerRepository.findeNachId(konto13TageOhnePasskeyId).isPresent());

        // 30 Tage altes Konto MIT Passkey wird NIEMALS gelöscht
        assertTrue(benutzerRepository.findeNachId(konto30TageMitPasskeyId).isPresent());
        assertEquals(1, passkeyRepository.zaehleNachBenutzerId(konto30TageMitPasskeyId));

        // Zweiter Lauf unmittelbar danach löscht nichts mehr von diesen Testdaten
        AufraeumErgebnis zweiterLauf = aufraeumService.aufraeumen();
        assertEquals(0, zweiterLauf.geloeschteRegistrierungen());
        assertEquals(0, zweiterLauf.geloeschteKontenOhnePasskey());
        assertFalse(benutzerRepository.findeNachId(konto30TageMitPasskeyId).isEmpty());
    }

    @Transactional
    void testDatenAnlegen(
            Instant jetzt,
            String suffix,
            Benutzername regAbgelaufenName,
            Benutzername regAktivName,
            BenutzerId konto15TageOhnePasskeyId,
            BenutzerId konto13TageOhnePasskeyId,
            BenutzerId konto30TageMitPasskeyId
    ) {
        // 1. Abgelaufene ausstehende Registrierung (vor 11 Min. erstellt, vor 1 Min. abgelaufen)
        ausstehendeRegistrierungRepository.speichern(new AusstehendeRegistrierung(
                BenutzerId.neu(),
                regAbgelaufenName,
                jetzt.minus(Duration.ofMinutes(11)),
                jetzt.minus(Duration.ofMinutes(11)),
                jetzt.minus(Duration.ofMinutes(1))
        ));

        // 2. Aktive ausstehende Registrierung (vor 5 Min. erstellt, läuft in 5 Min. ab)
        ausstehendeRegistrierungRepository.speichern(new AusstehendeRegistrierung(
                BenutzerId.neu(),
                regAktivName,
                jetzt.minus(Duration.ofMinutes(5)),
                jetzt.minus(Duration.ofMinutes(5)),
                jetzt.plus(Duration.ofMinutes(5))
        ));

        // 3. Konto ohne Passkey, 15 Tage alt -> muss gelöscht werden
        Instant vor15Tagen = jetzt.minus(Duration.ofDays(15));
        benutzerRepository.speichern(Benutzer.neuRegistriert(
                konto15TageOhnePasskeyId,
                new Benutzername("alt15." + suffix),
                vor15Tagen,
                vor15Tagen
        ));

        // 4. Konto ohne Passkey, 13 Tage alt -> muss erhalten bleiben
        Instant vor13Tagen = jetzt.minus(Duration.ofDays(13));
        benutzerRepository.speichern(Benutzer.neuRegistriert(
                konto13TageOhnePasskeyId,
                new Benutzername("alt13." + suffix),
                vor13Tagen,
                vor13Tagen
        ));

        // 5. Konto MIT Passkey, 30 Tage alt -> darf NIEMALS gelöscht werden
        Instant vor30Tagen = jetzt.minus(Duration.ofDays(30));
        benutzerRepository.speichern(Benutzer.neuRegistriert(
                konto30TageMitPasskeyId,
                new Benutzername("mitpk." + suffix),
                vor30Tagen,
                vor30Tagen
        ));
        passkeyRepository.speichern(new Passkey(
                PasskeyId.neu(),
                konto30TageMitPasskeyId,
                "cred-aufraeum-" + suffix,
                new byte[] {1, 2, 3, 4},
                -7L,
                0L,
                UUID.randomUUID(),
                new PasskeyBezeichnung("Passkey 1"),
                LocalDate.of(2026, 10, 20),
                null
        ));
    }
}
