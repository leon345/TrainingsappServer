package de.trainingsapp.application.passkey;

import de.trainingsapp.application.Transaktion;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.benutzer.UngueltigerBenutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.UngueltigeBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Set<Benutzername> gesperrteNamen;
    private final Transaktion transaktion;
    private final Clock uhr;

    @Inject
    PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys,
                   AusstehendeRegistrierungRepository ausstehende, PasskeyZeremonie zeremonie,
                   Anmeldesitzung sitzung, Transaktion transaktion,
                   @ConfigProperty(name = "trainingsapp.passkey.ausstehende-registrierung-ttl")
                   Duration registrierungTtl,
                   @ConfigProperty(name = "trainingsapp.benutzername.gesperrt") List<String> gesperrteNamen) {
        this(benutzer, passkeys, ausstehende, zeremonie, sitzung, transaktion, registrierungTtl,
                gesperrteNamen.stream().map(Benutzername::aus).collect(Collectors.toUnmodifiableSet()),
                Clock.systemUTC());
    }

    public PasskeyService(BenutzerRepository benutzer, PasskeyRepository passkeys,
                          AusstehendeRegistrierungRepository ausstehende, PasskeyZeremonie zeremonie,
                          Anmeldesitzung sitzung, Transaktion transaktion, Duration registrierungTtl,
                          Set<Benutzername> gesperrteNamen, Clock uhr) {
        if (registrierungTtl.isNegative() || registrierungTtl.isZero()) {
            throw new IllegalArgumentException("TTL der ausstehenden Registrierung muss positiv sein");
        }
        this.benutzer = benutzer;
        this.passkeys = passkeys;
        this.ausstehende = ausstehende;
        this.zeremonie = zeremonie;
        this.sitzung = sitzung;
        this.transaktion = transaktion;
        this.registrierungTtl = registrierungTtl;
        this.gesperrteNamen = Set.copyOf(gesperrteNamen);
        this.uhr = uhr;
    }

    // --- Registrierung eines neuen Benutzers (unauthentifiziert) ---

    /**
     * @throws UngueltigerBenutzername wenn der Name gegen E28 verstoesst oder gesperrt ist
     * @throws BenutzernameVergeben wenn der Name schon vergeben ist
     */
    public String registrierungStarten(String eingabe) {
        Benutzername benutzername = Benutzername.aus(eingabe);
        if (gesperrteNamen.contains(benutzername)) {
            throw new UngueltigerBenutzername("Dieser Benutzername ist nicht verfuegbar");
        }
        if (benutzer.findeNachName(benutzername).isPresent()) {
            throw new BenutzernameVergeben(benutzername.wert());
        }
        // Die BenutzerId wird schon hier vergeben, damit der User-Handle des ersten Passkeys
        // gleich dem aller weiteren ist. Sie bleibt bis zum Abschluss, hoechstens bis zum Ablauf
        // der TTL reserviert; ein erneuter Versuch innerhalb der TTL erhaelt dieselbe BenutzerId.
        Instant jetzt = uhr.instant();
        AusstehendeRegistrierung reserviert = transaktion.ausfuehren(() -> ausstehende.reserviere(
                new AusstehendeRegistrierung(BenutzerId.neu(), benutzername, jetzt.plus(registrierungTtl)), jetzt));
        // E26: Optionen (externer Aufruf) ausserhalb jeder Transaktion
        PasskeyZeremonie.Optionen optionen =
                zeremonie.registrierungsOptionen(new Benutzer(reserviert.vorgesehen(), benutzername), List.of());
        // B1: Der Abschluss gelingt nur mit einer Antwort auf genau diese Challenge (Name an Start gebunden)
        transaktion.ausfuehren(() -> ausstehende.bindeChallenge(reserviert.vorgesehen(), optionen.challenge()));
        return optionen.json();
    }

    /**
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort oder ohne gueltige (nicht
     *                                       abgelaufene) ausstehende Registrierung fuer den Namen
     * @throws BenutzernameVergeben          wenn der Name inzwischen vergeben ist
     */
    public BenutzerId registrierungAbschliessen(String eingabe, String antwortJson) {
        Benutzername benutzername = Benutzername.aus(eingabe);
        // E26: Pruefung (und Challenge-Verbrauch) ausserhalb der Transaktion
        PasskeyZeremonie.NeuerPasskey neu = zeremonie.registrierungPruefen(antwortJson);
        Instant jetzt = uhr.instant();
        // E26: Reservierung lesen und loeschen, Benutzer anlegen und Passkey speichern gemeinsam;
        // scheitert ein Schritt, bleibt weder ein Benutzer ohne Passkey noch eine verbrauchte Reservierung.
        BenutzerId angelegt = transaktion.ausfuehren(() -> {
            AusstehendeRegistrierung reserviert = ausstehende.entnimm(benutzername, neu.challenge(), jetzt)
                    .orElseThrow(() -> new PasskeyPruefungFehlgeschlagen(
                            "Keine zu dieser Challenge passende, gueltige Registrierung fuer diesen Benutzernamen"));
            Benutzer neuerBenutzer = new Benutzer(reserviert.vorgesehen(), benutzername);
            if (!benutzer.legeAn(neuerBenutzer)) {
                throw new BenutzernameVergeben(benutzername.wert());
            }
            speichere(neuerBenutzer.id(), neu);
            return neuerBenutzer.id();
        });
        // E26: Sitzung erst nach dem Commit
        sitzung.starte(angelegt);
        return angelegt;
    }

    // --- Anmeldung (unauthentifiziert) ---

    public String anmeldungStarten() {
        return zeremonie.anmeldeOptionen();
    }

    public BenutzerId anmeldungAbschliessen(String antwortJson) {
        Passkey verwendet = zeremonie.anmeldungPruefen(antwortJson);
        transaktion.ausfuehren(() -> passkeys.vermerkeAnmeldung(verwendet.id(), verwendet.zaehler(), heute()));
        sitzung.starte(verwendet.benutzerId());
        return verwendet.benutzerId();
    }

    // --- Verwaltung (nur angemeldet; benutzerId stammt aus der Sitzung) ---

    public String weiterenPasskeyStarten(BenutzerId angemeldet) {
        Benutzer b = benutzer.findeNachId(angemeldet)
                .orElseThrow(() -> new IllegalStateException("Angemeldeter Benutzer unbekannt: " + angemeldet));
        List<PasskeyId> vorhandene = passkeys.findeFuer(angemeldet).stream().map(Passkey::id).toList();
        return zeremonie.registrierungsOptionen(b, vorhandene).json();
    }

    public Passkey weiterenPasskeyAbschliessen(BenutzerId angemeldet, String antwortJson) {
        PasskeyZeremonie.NeuerPasskey neu = zeremonie.registrierungPruefen(antwortJson);
        return transaktion.ausfuehren(() -> speichere(angemeldet, neu));
    }

    public List<Passkey> liste(BenutzerId angemeldet) {
        return passkeys.findeFuer(angemeldet);
    }

    /**
     * Benennt einen eigenen Passkey um (E29).
     *
     * @throws UngueltigeBezeichnung wenn die Bezeichnung gegen die Regeln verstoesst
     * @throws PasskeyNichtGefunden  wenn es keinen eigenen Passkey mit dieser ID gibt
     */
    public Passkey benenneUm(BenutzerId angemeldet, PasskeyId id, String eingabe) {
        PasskeyBezeichnung bezeichnung = PasskeyBezeichnung.aus(eingabe);
        return transaktion.ausfuehren(() -> {
            if (!passkeys.benenneUm(angemeldet, id, bezeichnung)) {
                throw new PasskeyNichtGefunden(id.wert());
            }
            return passkeys.findeNachId(id).orElseThrow();
        });
    }

    public void loesche(BenutzerId angemeldet, PasskeyId id) {
        switch (transaktion.ausfuehren(() -> passkeys.loescheWennNichtLetzter(angemeldet, id))) {
            case GELOESCHT -> { }
            case NICHT_GEFUNDEN -> throw new PasskeyNichtGefunden(id.wert());
            case LETZTER_PASSKEY -> throw new LetzterPasskey(id.wert());
        }
    }

    /** Nur innerhalb einer Transaktion: vergibt "Passkey n" und speichert. */
    private Passkey speichere(BenutzerId benutzerId, PasskeyZeremonie.NeuerPasskey neu) {
        PasskeyBezeichnung bezeichnung = PasskeyBezeichnung.automatisch(benutzer.naechstePasskeyNummer(benutzerId));
        Passkey passkey = new Passkey(neu.id(), benutzerId, bezeichnung, neu.publicKey(), neu.algorithmus(),
                neu.zaehler(), heute(), null);
        if (!passkeys.fuegeHinzu(passkey)) {
            throw new PasskeyPruefungFehlgeschlagen("Passkey ist bereits registriert");
        }
        return passkey;
    }

    /** Heutiger Tag in UTC (E29). */
    private LocalDate heute() {
        return LocalDate.ofInstant(uhr.instant(), ZoneOffset.UTC);
    }
}
