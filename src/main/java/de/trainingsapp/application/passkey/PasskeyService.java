package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Clock uhr;
    private final Map<String, BenutzerId> ausstehend = new ConcurrentHashMap<>();

    @Inject
    PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys, PasskeyZeremonie zeremonie,
                   Anmeldesitzung sitzung) {
        this(benutzer, passkeys, zeremonie, sitzung, Clock.systemUTC());
    }

    public PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys, PasskeyZeremonie zeremonie,
                          Anmeldesitzung sitzung, Clock uhr) {
        this.benutzer = benutzer;
        this.passkeys = passkeys;
        this.zeremonie = zeremonie;
        this.sitzung = sitzung;
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
        // gleich dem aller weiteren ist. Spike: nur im Speicher, ohne Ablaufzeit.
        BenutzerId vorgesehen = ausstehend.computeIfAbsent(benutzername,
                n -> BenutzerId.neu());
        return zeremonie.registrierungsOptionen(new Benutzer(vorgesehen, benutzername), List.of());
    }

    public BenutzerId registrierungAbschliessen(String benutzername, String antwortJson) {
        PasskeyZeremonie.NeuerPasskey neu = zeremonie.registrierungPruefen(antwortJson);
        BenutzerId vorgesehen = ausstehend.remove(benutzername);
        if (vorgesehen == null) {
            throw new PasskeyPruefungFehlgeschlagen("Keine laufende Registrierung fuer diesen Benutzernamen");
        }
        Benutzer angelegt = new Benutzer(vorgesehen, benutzername);
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
