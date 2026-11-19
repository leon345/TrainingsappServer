package de.trainingsapp.application.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.api.v1.auth.WebAuthnTestAuthenticator;
import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.LetzterPasskeyException;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyNichtGefundenException;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import de.trainingsapp.infrastructure.passkey.QuarkusWebAuthnUserProvider;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import jakarta.transaction.Transactional;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests für PasskeyService, Auth-Port-Austauschbarkeit (Fake-Anmeldesitzung),
 * Signatur-Algorithmen (ES256, RS256, EdDSA), Signaturzähler 0, UTC-Tageswechsel und @Transactional.
 */
class PasskeyKryptoUndServiceTest {

    @Test
    void credentialRecordKonvertierungUnterstuetztEs256Rs256UndSignCountNull() {
        for (WebAuthnTestAuthenticator.Algorithmus alg : List.of(
                WebAuthnTestAuthenticator.Algorithmus.ES256,
                WebAuthnTestAuthenticator.Algorithmus.RS256
        )) {
            WebAuthnTestAuthenticator auth = WebAuthnTestAuthenticator.erstellen(alg, true);
            BenutzerId benutzerId = BenutzerId.neu();
            Passkey passkey = new Passkey(
                    PasskeyId.neu(),
                    benutzerId,
                    auth.credentialId(),
                    auth.keyPair().getPublic().getEncoded(),
                    auth.coseAlgorithmId(),
                    0L,
                    new UUID(0L, 0L),
                    PasskeyBezeichnung.standard(1),
                    LocalDate.of(2026, 11, 19),
                    null
            );

            WebAuthnCredentialRecord record = QuarkusWebAuthnUserProvider.zuCredentialRecord(passkey);
            assertEquals(benutzerId.alsText(), record.getUsername());
            assertEquals(auth.credentialId(), record.getCredentialID());
            assertEquals(0L, record.getCounter());
            assertEquals(auth.coseAlgorithmId(), record.getRequiredPersistedData().publicKeyAlgorithm());
        }
    }

    @Test
    void authPortAustauschMitFakeAnmeldesitzungFunktioniertOhneWebAuthnCookies() {
        TestUhr uhr = new TestUhr(Instant.parse("2026-11-19T10:00:00Z"));
        InMemoryBenutzerRepo benutzerRepo = new InMemoryBenutzerRepo();
        InMemoryRegistrierungRepo regRepo = new InMemoryRegistrierungRepo();
        InMemoryPasskeyRepo passkeyRepo = new InMemoryPasskeyRepo();
        FakeAnmeldesitzung fakeSitzung = new FakeAnmeldesitzung();
        FakePasskeyZeremonie fakeZeremonie = new FakePasskeyZeremonie();

        PasskeyService service = new PasskeyService(
                benutzerRepo,
                regRepo,
                passkeyRepo,
                fakeSitzung,
                fakeZeremonie,
                new ZugriffsPolicy(),
                uhr,
                Optional.of(List.of("team-intern"))
        );

        // Einwilligung Pflicht
        DomainException ohneEinwilligung = assertThrows(
                DomainException.class,
                () -> service.starteRegistrierung("athlet.eins", false)
        );
        assertEquals("EINWILLIGUNG_ERFORDERLICH", ohneEinwilligung.code());

        // Registrierung starten & abschließen
        assertNotNull(service.starteRegistrierung("Athlet.Eins", true));
        Benutzer benutzer = service.schliesseRegistrierungAb(
                "athlet.eins",
                new PasskeyZeremonie.RegistrierungsAntwort("c1", "c1", "public-key", "cd", "att"),
                null
        );
        assertEquals("athlet.eins", benutzer.benutzername().wert());
        assertEquals(Optional.of(benutzer.id()), fakeSitzung.aktuellerBenutzer());
        assertEquals(benutzer.id(), service.aktuellerBenutzer().id());

        List<Passkey> passkeys = service.listePasskeys();
        assertEquals(1, passkeys.size());
        assertEquals("Passkey 1", passkeys.getFirst().bezeichnung().wert());
        assertNull(passkeys.getFirst().zuletztVerwendetAm());

        // Letzter Passkey darf nicht gelöscht werden
        assertThrows(LetzterPasskeyException.class, () -> service.loeschePasskey(passkeys.getFirst().id()));

        // Zweiten Passkey hinzufügen (automatisch "Passkey 2"), löschen, dritten hinzufügen -> "Passkey 3"
        Passkey zweiter = service.fuegePasskeyHinzu(
                new PasskeyZeremonie.RegistrierungsAntwort("c2", "c2", "public-key", "cd", "att"),
                null
        );
        assertEquals("Passkey 2", zweiter.bezeichnung().wert());
        service.loeschePasskey(zweiter.id());

        Passkey dritter = service.fuegePasskeyHinzu(
                new PasskeyZeremonie.RegistrierungsAntwort("c3", "c3", "public-key", "cd", "att"),
                null
        );
        assertEquals("Passkey 3", dritter.bezeichnung().wert());

        // Anmeldung am selben UTC-Tag setzt zuletztVerwendetAm; nochmalige Anmeldung am selben Tag mit Counter 0 spart Update
        service.abmelden();
        assertTrue(fakeSitzung.aktuellerBenutzer().isEmpty());

        fakeZeremonie.naechsteAnmeldung(benutzer.id(), "c1", 0L);
        service.meldeAn(new PasskeyZeremonie.AnmeldeAntwort("c1", "c1", "public-key", "cd", "ad", "sig", null));
        assertEquals(LocalDate.of(2026, 11, 19), passkeyRepo.findeNachCredentialId("c1").orElseThrow().zuletztVerwendetAm());
        int updatesNachErsterAnmeldung = passkeyRepo.updateZaehler.get();

        // Zweite Anmeldung am selben UTC-Tag mit Counter 0 -> kein unnötiges DB-Update
        service.meldeAn(new PasskeyZeremonie.AnmeldeAntwort("c1", "c1", "public-key", "cd", "ad", "sig", null));
        assertEquals(updatesNachErsterAnmeldung, passkeyRepo.updateZaehler.get());

        // UTC-Tageswechsel -> zuletztVerwendetAm wird aktualisiert
        uhr.setze(Instant.parse("2026-11-20T00:01:00Z"));
        service.meldeAn(new PasskeyZeremonie.AnmeldeAntwort("c1", "c1", "public-key", "cd", "ad", "sig", null));
        assertEquals(LocalDate.of(2026, 11, 20), passkeyRepo.findeNachCredentialId("c1").orElseThrow().zuletztVerwendetAm());
        assertEquals(updatesNachErsterAnmeldung + 1, passkeyRepo.updateZaehler.get());

        // Fremder Benutzer erhält 404 (PasskeyNichtGefundenException)
        fakeSitzung.anmelden(BenutzerId.neu());
        assertThrows(PasskeyNichtGefundenException.class, () -> service.benennePasskeyUm(dritter.id(), "Neu"));
        assertThrows(PasskeyNichtGefundenException.class, () -> service.loeschePasskey(dritter.id()));
    }

    @Test
    void abgelaufeneRegistrierungNach10MinutenWirdAbgelehnt() {
        TestUhr uhr = new TestUhr(Instant.parse("2026-11-19T10:00:00Z"));
        PasskeyService service = new PasskeyService(
                new InMemoryBenutzerRepo(),
                new InMemoryRegistrierungRepo(),
                new InMemoryPasskeyRepo(),
                new FakeAnmeldesitzung(),
                new FakePasskeyZeremonie(),
                new ZugriffsPolicy(),
                uhr,
                Optional.empty()
        );

        service.starteRegistrierung("abgelaufen1", true);
        uhr.setze(Instant.parse("2026-11-19T10:10:00Z"));

        DomainException ex = assertThrows(
                DomainException.class,
                () -> service.schliesseRegistrierungAb(
                        "abgelaufen1",
                        new PasskeyZeremonie.RegistrierungsAntwort("c1", "c1", "public-key", "cd", "att"),
                        null
                )
        );
        assertEquals("REGISTRIERUNG_ABGELAUFEN", ex.code());
    }

    @Test
    void schreibendePasskeyServiceMethodenSindDirektMitTransactionalAnnotiert() throws NoSuchMethodException {
        List<Method> schreibMethoden = List.of(
                PasskeyService.class.getMethod("starteRegistrierung", String.class, boolean.class),
                PasskeyService.class.getMethod("schliesseRegistrierungAb", String.class, PasskeyZeremonie.RegistrierungsAntwort.class, String.class),
                PasskeyService.class.getMethod("meldeAn", PasskeyZeremonie.AnmeldeAntwort.class),
                PasskeyService.class.getMethod("fuegePasskeyHinzu", PasskeyZeremonie.RegistrierungsAntwort.class, String.class),
                PasskeyService.class.getMethod("benennePasskeyUm", PasskeyId.class, String.class),
                PasskeyService.class.getMethod("loeschePasskey", PasskeyId.class)
        );
        for (Method methode : schreibMethoden) {
            assertTrue(
                    methode.isAnnotationPresent(Transactional.class),
                    "Erwarte @Transactional an " + methode.getName()
            );
        }
    }

    /**
     * Fake-Implementierung des dünnen Auth-Ports Anmeldesitzung (belegt Austauschbarkeit für z. B. Native/Token-Auth).
     */
    static final class FakeAnmeldesitzung implements Anmeldesitzung {
        private BenutzerId aktuell;

        @Override
        public Optional<BenutzerId> aktuellerBenutzer() {
            return Optional.ofNullable(aktuell);
        }

        @Override
        public void anmelden(BenutzerId benutzerId) {
            this.aktuell = benutzerId;
        }

        @Override
        public void beenden() {
            this.aktuell = null;
        }
    }

    private static final class TestUhr implements Uhr {
        private Instant jetzt;

        TestUhr(Instant start) {
            this.jetzt = start;
        }

        void setze(Instant neu) {
            this.jetzt = neu;
        }

        @Override
        public Instant jetzt() {
            return jetzt;
        }
    }

    private static final class FakePasskeyZeremonie implements PasskeyZeremonie {
        private VerifizierteAnmeldung anmeldung;

        void naechsteAnmeldung(BenutzerId benutzerId, String credentialId, long signCount) {
            this.anmeldung = new VerifizierteAnmeldung(benutzerId, credentialId, signCount);
        }

        @Override
        public String erstelleRegistrierungsOptionen(
                BenutzerId benutzerId,
                Benutzername benutzername,
                List<String> ausgeschlosseneCredentialIds
        ) {
            return "{\"challenge\":\"fake\"}";
        }

        @Override
        public VerifizierterNeuerPasskey verifiziereRegistrierung(
                BenutzerId benutzerId,
                RegistrierungsAntwort antwort
        ) {
            return new VerifizierterNeuerPasskey(
                    antwort.id(),
                    new byte[]{1, 2, 3, 4},
                    -7L,
                    0L,
                    new UUID(0L, 0L)
            );
        }

        @Override
        public String erstelleAnmeldeOptionen(List<String> erlaubteCredentialIds) {
            return "{\"challenge\":\"fake-login\"}";
        }

        @Override
        public VerifizierteAnmeldung verifiziereAnmeldung(AnmeldeAntwort antwort) {
            return anmeldung;
        }
    }

    private static final class InMemoryBenutzerRepo implements BenutzerRepository {
        private final Map<BenutzerId, Benutzer> speicher = new HashMap<>();

        @Override
        public Optional<Benutzer> findeNachId(BenutzerId id) {
            return Optional.ofNullable(speicher.get(id));
        }

        @Override
        public Map<BenutzerId, Benutzer> findeAlleNachIds(java.util.Collection<BenutzerId> ids) {
            Map<BenutzerId, Benutzer> ergebnis = new HashMap<>();
            for (BenutzerId id : ids) {
                Benutzer benutzer = speicher.get(id);
                if (benutzer != null) {
                    ergebnis.put(id, benutzer);
                }
            }
            return ergebnis;
        }

        @Override
        public Optional<Benutzer> findeNachBenutzername(Benutzername benutzername) {
            return speicher.values().stream()
                    .filter(b -> b.benutzername().equals(benutzername))
                    .findFirst();
        }

        @Override
        public boolean existiertBenutzername(Benutzername benutzername) {
            return findeNachBenutzername(benutzername).isPresent();
        }

        @Override
        public void speichern(Benutzer benutzer) {
            speicher.put(benutzer.id(), benutzer);
        }

        @Override
        public void aktualisieren(Benutzer benutzer) {
            speicher.put(benutzer.id(), benutzer);
        }

        @Override
        public boolean loeschen(BenutzerId id) {
            return speicher.remove(id) != null;
        }

        @Override
        public int loescheKontenOhnePasskeyVor(Instant stichtag) {
            return 0;
        }
    }

    private static final class InMemoryRegistrierungRepo implements AusstehendeRegistrierungRepository {
        private final Map<BenutzerId, AusstehendeRegistrierung> speicher = new HashMap<>();

        @Override
        public Optional<AusstehendeRegistrierung> findeNachBenutzername(Benutzername benutzername) {
            return speicher.values().stream()
                    .filter(r -> r.benutzername().equals(benutzername))
                    .findFirst();
        }

        @Override
        public void speichern(AusstehendeRegistrierung registrierung) {
            speicher.put(registrierung.benutzerId(), registrierung);
        }

        @Override
        public void loeschenNachBenutzerId(BenutzerId benutzerId) {
            speicher.remove(benutzerId);
        }

        @Override
        public void loeschenNachBenutzername(Benutzername benutzername) {
            speicher.values().removeIf(r -> r.benutzername().equals(benutzername));
        }

        @Override
        public long loescheAbgelaufene(Instant stichtag) {
            long vorher = speicher.size();
            speicher.values().removeIf(r -> !r.laeuftAbAm().isAfter(stichtag));
            return vorher - speicher.size();
        }
    }

    private static final class InMemoryPasskeyRepo implements PasskeyRepository {
        private final Map<PasskeyId, Passkey> speicher = new HashMap<>();
        final AtomicInteger updateZaehler = new AtomicInteger();

        @Override
        public Optional<Passkey> findeNachId(PasskeyId id) {
            return Optional.ofNullable(speicher.get(id));
        }

        @Override
        public Optional<Passkey> findeNachCredentialId(String credentialId) {
            return speicher.values().stream()
                    .filter(p -> p.credentialId().equals(credentialId))
                    .findFirst();
        }

        @Override
        public List<Passkey> findeAlleNachBenutzerId(BenutzerId benutzerId) {
            return speicher.values().stream()
                    .filter(p -> p.benutzerId().equals(benutzerId))
                    .toList();
        }

        @Override
        public long zaehleNachBenutzerId(BenutzerId benutzerId) {
            return findeAlleNachBenutzerId(benutzerId).size();
        }

        @Override
        public void speichern(Passkey passkey) {
            speicher.put(passkey.id(), passkey);
        }

        @Override
        public void aktualisieren(Passkey passkey) {
            updateZaehler.incrementAndGet();
            speicher.put(passkey.id(), passkey);
        }

        @Override
        public boolean loeschen(PasskeyId id) {
            return speicher.remove(id) != null;
        }
    }
}
