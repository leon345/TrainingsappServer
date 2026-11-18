package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Anwendungsfaelle rund um Passkeys: Registrieren (neuer Benutzer), Anmelden,
 * weiteren Passkey hinzufuegen (angemeldet), auflisten, loeschen mit Loeschsperre.
 */
@ApplicationScoped
public class PasskeyService {

    private final BenutzerRepository benutzer;
    private final PasskeyRepository passkeys;
    private final PasskeyZeremonie zeremonie;
    private final Anmeldesitzung sitzung;
    private final AusstehendeRegistrierungRepository ausstehende;
    private final Duration registrierungTtl;
    private final Clock uhr;

    @Inject
    PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys,
                   AusstehendeRegistrierungRepository ausstehende, PasskeyZeremonie zeremonie,
                   Anmeldesitzung sitzung,
                   @ConfigProperty(name = "trainingsapp.passkey.ausstehende-registrierung-ttl")
                   Duration registrierungTtl) {
        this(benutzer, passkeys, ausstehende, zeremonie, sitzung, registrierungTtl, Clock.systemUTC());
    }

    public PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys,
                          AusstehendeRegistrierungRepository ausstehende, PasskeyZeremonie zeremonie,
                          Anmeldesitzung sitzung, Duration registrierungTtl, Clock uhr) {
        if (registrierungTtl.isNegative() || registrierungTtl.isZero()) {
            throw new IllegalArgumentException("TTL der ausstehenden Registrierung muss positiv sein");
        }
        this.benutzer = benutzer;
        this.passkeys = passkeys;
        this.ausstehende = ausstehende;
        this.zeremonie = zeremonie;
        this.sitzung = sitzung;
        this.registrierungTtl = registrierungTtl;
        this.uhr = uhr;
    }

    // --- Registrierung eines neuen Benutzers (unauthentifiziert) ---

    /**
     * @throws de.trainingsapp.domain.benutzer.UngueltigerBenutzername wenn der Name gegen E17 verstoesst
     * @throws BenutzernameVergeben wenn der Name schon vergeben ist
     */
    public String registrierungStarten(String benutzername) {
        Benutzername.pruefe(benutzername);
        if (benutzer.findeNachName(benutzername).isPresent()) {
            throw new BenutzernameVergeben(benutzername);
        }
        // Die BenutzerId wird schon hier vergeben, damit der User-Handle des ersten Passkeys
        // gleich dem aller weiteren ist. Sie bleibt bis zum Abschluss, hoechstens bis zum Ablauf
        // der TTL reserviert; ein erneuter Versuch innerhalb der TTL erhaelt dieselbe BenutzerId.
        Instant jetzt = uhr.instant();
        AusstehendeRegistrierung reserviert = ausstehende.reserviere(
                new AusstehendeRegistrierung(BenutzerId.neu(), benutzername, jetzt.plus(registrierungTtl)), jetzt);
        return zeremonie.registrierungsOptionen(new Benutzer(reserviert.vorgesehen(), benutzername), List.of());
    }

    /**
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort oder ohne gueltige (nicht
     *                                       abgelaufene) ausstehende Registrierung fuer den Namen
     * @throws BenutzernameVergeben          wenn der Name inzwischen vergeben ist
     */
    public BenutzerId registrierungAbschliessen(String benutzername, String antwortJson) {
        Benutzername.pruefe(benutzername);
        PasskeyZeremonie.NeuerPasskey neu = zeremonie.registrierungPruefen(antwortJson);
        AusstehendeRegistrierung reserviert = ausstehende.entnimm(benutzername, uhr.instant())
                .orElseThrow(() -> new PasskeyPruefungFehlgeschlagen(
                        "Keine laufende oder eine abgelaufene Registrierung fuer diesen Benutzernamen"));
        Benutzer angelegt = new Benutzer(reserviert.vorgesehen(), benutzername);
        if (!benutzer.legeAn(angelegt)) {
            throw new BenutzernameVergeben(benutzername);
        }
        speichere(angelegt.id(), neu);
        sitzung.starte(angelegt.id());
        return angelegt.id();
    }

    // --- Anmeldung (unauthentifiziert) ---

    public String anmeldungStarten() {
        return zeremonie.anmeldeOptionen();
    }

    public BenutzerId anmeldungAbschliessen(String antwortJson) {
        Passkey verwendet = zeremonie.anmeldungPruefen(antwortJson);
        passkeys.aktualisiereZaehler(verwendet.id(), verwendet.zaehler());
        sitzung.starte(verwendet.benutzerId());
        return verwendet.benutzerId();
    }

    // --- Verwaltung (nur angemeldet; benutzerId stammt aus der Sitzung) ---

    public String weiterenPasskeyStarten(BenutzerId angemeldet) {
        Benutzer b = benutzer.findeNachId(angemeldet)
                .orElseThrow(() -> new IllegalStateException("Angemeldeter Benutzer unbekannt: " + angemeldet));
        List<PasskeyId> vorhandene = passkeys.findeFuer(angemeldet).stream().map(Passkey::id).toList();
        return zeremonie.registrierungsOptionen(b, vorhandene);
    }

    public Passkey weiterenPasskeyAbschliessen(BenutzerId angemeldet, String antwortJson) {
        PasskeyZeremonie.NeuerPasskey neu = zeremonie.registrierungPruefen(antwortJson);
        return speichere(angemeldet, neu);
    }

    public List<Passkey> liste(BenutzerId angemeldet) {
        return passkeys.findeFuer(angemeldet).stream()
                .sorted(Comparator.comparing(Passkey::erstelltAm))
                .toList();
    }

    public void loesche(BenutzerId angemeldet, PasskeyId id) {
        switch (passkeys.loescheWennNichtLetzter(angemeldet, id)) {
            case GELOESCHT -> { }
            case NICHT_GEFUNDEN -> throw new PasskeyNichtGefunden(id.wert());
            case LETZTER_PASSKEY -> throw new LetzterPasskey(id.wert());
        }
    }

    private Passkey speichere(BenutzerId benutzerId, PasskeyZeremonie.NeuerPasskey neu) {
        Passkey passkey = new Passkey(neu.id(), benutzerId, neu.aaguid(), neu.publicKey(), neu.algorithmus(),
                neu.zaehler(), uhr.instant());
        if (!passkeys.fuegeHinzu(passkey)) {
            throw new PasskeyPruefungFehlgeschlagen("Passkey ist bereits registriert");
        }
        return passkey;
    }
}
