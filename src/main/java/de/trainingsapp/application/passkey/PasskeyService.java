package de.trainingsapp.application.passkey;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.DomainKonfliktException;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.benutzer.UngueltigerBenutzernameException;
import de.trainingsapp.domain.passkey.LetzterPasskeyException;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyNichtGefundenException;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Anwendungsservice für Registrierung, Anmeldung, Abmeldung und Multi-Passkey-Verwaltung.
 */
@ApplicationScoped
public class PasskeyService {

    private final BenutzerRepository benutzerRepository;
    private final AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository;
    private final PasskeyRepository passkeyRepository;
    private final Anmeldesitzung anmeldesitzung;
    private final PasskeyZeremonie passkeyZeremonie;
    private final ZugriffsPolicy zugriffsPolicy;
    private final Uhr uhr;
    private final Set<String> konfigurierteSperrliste;

    @Inject
    public PasskeyService(
            BenutzerRepository benutzerRepository,
            AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository,
            PasskeyRepository passkeyRepository,
            Anmeldesitzung anmeldesitzung,
            PasskeyZeremonie passkeyZeremonie,
            ZugriffsPolicy zugriffsPolicy,
            Uhr uhr,
            @ConfigProperty(name = "trainingsapp.benutzername.sperrliste") Optional<List<String>> sperrliste
    ) {
        this.benutzerRepository = benutzerRepository;
        this.ausstehendeRegistrierungRepository = ausstehendeRegistrierungRepository;
        this.passkeyRepository = passkeyRepository;
        this.anmeldesitzung = anmeldesitzung;
        this.passkeyZeremonie = passkeyZeremonie;
        this.zugriffsPolicy = zugriffsPolicy;
        this.uhr = uhr;
        this.konfigurierteSperrliste = sperrliste
                .map(liste -> Set.copyOf(liste.stream().filter(s -> s != null && !s.isBlank()).toList()))
                .orElseGet(Set::of);
    }

    @Transactional
    public String starteRegistrierung(String benutzernameEingabe, boolean einwilligungErteilt) {
        if (!einwilligungErteilt) {
            throw new DomainException(
                    "EINWILLIGUNG_ERFORDERLICH",
                    "Für die Registrierung ist die ausdrückliche Einwilligung erforderlich."
            );
        }
        Benutzername benutzername = Benutzername.erstellen(benutzernameEingabe, konfigurierteSperrliste);
        if (benutzerRepository.existiertBenutzername(benutzername)) {
            throw new DomainKonfliktException(
                    "BENUTZERNAME_VERGEBEN",
                    "Dieser Benutzername ist bereits vergeben."
            );
        }

        Instant jetzt = uhr.jetzt();
        ausstehendeRegistrierungRepository.loeschenNachBenutzername(benutzername);
        AusstehendeRegistrierung registrierung = AusstehendeRegistrierung.neu(BenutzerId.neu(), benutzername, jetzt);
        ausstehendeRegistrierungRepository.speichern(registrierung);

        return passkeyZeremonie.erstelleRegistrierungsOptionen(
                registrierung.benutzerId(),
                registrierung.benutzername(),
                List.of()
        );
    }

    @Transactional
    public Benutzer schliesseRegistrierungAb(
            String benutzernameEingabe,
            PasskeyZeremonie.RegistrierungsAntwort antwort,
            String optionaleBezeichnung
    ) {
        Benutzername benutzername = Benutzername.erstellen(benutzernameEingabe, konfigurierteSperrliste);
        AusstehendeRegistrierung ausstehend = ausstehendeRegistrierungRepository
                .findeNachBenutzername(benutzername)
                .orElseThrow(() -> new DomainException(
                        "REGISTRIERUNG_UNGUELTIG",
                        "Keine gültige ausstehende Registrierung gefunden."
                ));

        Instant jetzt = uhr.jetzt();
        if (ausstehend.istAbgelaufen(jetzt)) {
            ausstehendeRegistrierungRepository.loeschenNachBenutzerId(ausstehend.benutzerId());
            throw new DomainException(
                    "REGISTRIERUNG_ABGELAUFEN",
                    "Die Registrierung ist abgelaufen. Bitte starte die Registrierung erneut."
            );
        }

        if (benutzerRepository.existiertBenutzername(benutzername)) {
            throw new DomainKonfliktException(
                    "BENUTZERNAME_VERGEBEN",
                    "Dieser Benutzername ist bereits vergeben."
            );
        }

        Benutzer neuerBenutzer = Benutzer.neuRegistriert(
                ausstehend.benutzerId(),
                ausstehend.benutzername(),
                jetzt,
                ausstehend.einwilligungAm()
        );
        PasskeyBezeichnung bezeichnung = bestimmePasskeyBezeichnung(neuerBenutzer, optionaleBezeichnung);

        PasskeyZeremonie.VerifizierterNeuerPasskey verifiziert =
                passkeyZeremonie.verifiziereRegistrierung(neuerBenutzer.id(), antwort);
        if (passkeyRepository.findeNachCredentialId(verifiziert.credentialId()).isPresent()) {
            throw new DomainKonfliktException(
                    "PASSKEY_BEREITS_VORHANDEN",
                    "Dieser Passkey ist bereits registriert."
            );
        }

        Benutzer gespeicherterBenutzer = neuerBenutzer.mitErhoehterPasskeyNummer();
        benutzerRepository.speichern(gespeicherterBenutzer);

        Passkey ersterPasskey = new Passkey(
                PasskeyId.neu(),
                gespeicherterBenutzer.id(),
                verifiziert.credentialId(),
                verifiziert.publicKey(),
                verifiziert.publicKeyAlgorithm(),
                verifiziert.signCount(),
                verifiziert.aaguid(),
                bezeichnung,
                uhr.heuteUtc(),
                null
        );
        passkeyRepository.speichern(ersterPasskey);
        ausstehendeRegistrierungRepository.loeschenNachBenutzerId(ausstehend.benutzerId());

        anmeldesitzung.anmelden(gespeicherterBenutzer.id());
        return gespeicherterBenutzer;
    }

    public String starteAnmeldung(String optionalerBenutzername) {
        List<String> erlaubteCredentialIds = List.of();
        if (optionalerBenutzername != null && !optionalerBenutzername.isBlank()) {
            try {
                Benutzername benutzername = new Benutzername(optionalerBenutzername);
                Optional<Benutzer> benutzer = benutzerRepository.findeNachBenutzername(benutzername);
                if (benutzer.isPresent()) {
                    erlaubteCredentialIds = passkeyRepository.findeAlleNachBenutzerId(benutzer.get().id())
                            .stream()
                            .map(Passkey::credentialId)
                            .toList();
                }
            } catch (UngueltigerBenutzernameException ignored) {
                // Keine Nutzeraufzählung über Fehlermeldungen bei Anmeldeoptionen.
            }
        }
        return passkeyZeremonie.erstelleAnmeldeOptionen(erlaubteCredentialIds);
    }

    @Transactional
    public Benutzer meldeAn(PasskeyZeremonie.AnmeldeAntwort antwort) {
        PasskeyZeremonie.VerifizierteAnmeldung verifiziert = passkeyZeremonie.verifiziereAnmeldung(antwort);

        Passkey passkey = passkeyRepository.findeNachCredentialId(verifiziert.credentialId())
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "ANMELDUNG_FEHLGESCHLAGEN",
                        "Die Anmeldung konnte nicht abgeschlossen werden."
                ));

        Benutzer benutzer = benutzerRepository.findeNachId(passkey.benutzerId())
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "ANMELDUNG_FEHLGESCHLAGEN",
                        "Die Anmeldung konnte nicht abgeschlossen werden."
                ));

        LocalDate heuteUtc = uhr.heuteUtc();
        if (passkey.brauchtVerwendungsAktualisierung(verifiziert.neuerSignCount(), heuteUtc)) {
            passkeyRepository.aktualisieren(passkey.mitVerwendung(verifiziert.neuerSignCount(), heuteUtc));
        }

        anmeldesitzung.anmelden(benutzer.id());
        return benutzer;
    }

    public void abmelden() {
        anmeldesitzung.beenden();
    }

    public Benutzer aktuellerBenutzer() {
        BenutzerId benutzerId = erforderlicherBenutzerId();
        return benutzerRepository.findeNachId(benutzerId)
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Keine gültige Anmeldung vorhanden."
                ));
    }

    public List<Passkey> listePasskeys() {
        BenutzerId benutzerId = erforderlicherBenutzerId();
        return passkeyRepository.findeAlleNachBenutzerId(benutzerId);
    }

    public String starteWeiterenPasskey() {
        Benutzer benutzer = aktuellerBenutzer();
        List<String> ausgeschlosseneCredentialIds = passkeyRepository.findeAlleNachBenutzerId(benutzer.id())
                .stream()
                .map(Passkey::credentialId)
                .toList();
        return passkeyZeremonie.erstelleRegistrierungsOptionen(
                benutzer.id(),
                benutzer.benutzername(),
                ausgeschlosseneCredentialIds
        );
    }

    @Transactional
    public Passkey fuegePasskeyHinzu(
            PasskeyZeremonie.RegistrierungsAntwort antwort,
            String optionaleBezeichnung
    ) {
        Benutzer benutzer = aktuellerBenutzer();
        PasskeyBezeichnung bezeichnung = bestimmePasskeyBezeichnung(benutzer, optionaleBezeichnung);

        PasskeyZeremonie.VerifizierterNeuerPasskey verifiziert =
                passkeyZeremonie.verifiziereRegistrierung(benutzer.id(), antwort);
        if (passkeyRepository.findeNachCredentialId(verifiziert.credentialId()).isPresent()) {
            throw new DomainKonfliktException(
                    "PASSKEY_BEREITS_VORHANDEN",
                    "Dieser Passkey ist bereits registriert."
            );
        }

        Benutzer aktualisiert = benutzer.mitErhoehterPasskeyNummer();
        benutzerRepository.aktualisieren(aktualisiert);

        Passkey neuerPasskey = new Passkey(
                PasskeyId.neu(),
                benutzer.id(),
                verifiziert.credentialId(),
                verifiziert.publicKey(),
                verifiziert.publicKeyAlgorithm(),
                verifiziert.signCount(),
                verifiziert.aaguid(),
                bezeichnung,
                uhr.heuteUtc(),
                null
        );
        passkeyRepository.speichern(neuerPasskey);
        return neuerPasskey;
    }

    @Transactional
    public Passkey benennePasskeyUm(PasskeyId passkeyId, String neueBezeichnungEingabe) {
        BenutzerId benutzerId = erforderlicherBenutzerId();
        PasskeyBezeichnung neueBezeichnung = new PasskeyBezeichnung(neueBezeichnungEingabe);
        Passkey passkey = ladeEigenenPasskey(benutzerId, passkeyId);

        Passkey umbenannt = passkey.umbenennen(neueBezeichnung);
        passkeyRepository.aktualisieren(umbenannt);
        return umbenannt;
    }

    @Transactional
    public void loeschePasskey(PasskeyId passkeyId) {
        BenutzerId benutzerId = erforderlicherBenutzerId();
        Passkey passkey = ladeEigenenPasskey(benutzerId, passkeyId);

        if (passkeyRepository.zaehleNachBenutzerId(benutzerId) <= 1) {
            throw new LetzterPasskeyException();
        }
        passkeyRepository.loeschen(passkey.id());
    }

    private Passkey ladeEigenenPasskey(BenutzerId benutzerId, PasskeyId passkeyId) {
        return passkeyRepository.findeNachId(passkeyId)
                .filter(passkey -> zugriffsPolicy.darfPasskeyVerwalten(benutzerId, passkey))
                .orElseThrow(PasskeyNichtGefundenException::new);
    }

    private PasskeyBezeichnung bestimmePasskeyBezeichnung(Benutzer benutzer, String optionaleBezeichnung) {
        if (optionaleBezeichnung != null && !optionaleBezeichnung.isBlank()) {
            return new PasskeyBezeichnung(optionaleBezeichnung);
        }
        return benutzer.standardPasskeyBezeichnung();
    }

    private BenutzerId erforderlicherBenutzerId() {
        return anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
    }
}
