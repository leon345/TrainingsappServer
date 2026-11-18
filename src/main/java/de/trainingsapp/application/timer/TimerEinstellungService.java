package de.trainingsapp.application.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.IntervallEinstellungRepository;
import de.trainingsapp.domain.timer.UngueltigeIntervallEinstellungException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.Optional;

/**
 * Anwendungsfaelle: Intervalltimer-Einstellung eines Benutzers lesen und speichern.
 *
 * <p>Der Benutzer wird immer explizit uebergeben. Die API-Schicht (S-02b) setzt
 * dort spaeter den angemeldeten Benutzer ein; dieser Service prueft keine
 * Berechtigungen (das ist Aufgabe von S-04/S-05).
 */
@ApplicationScoped
public class TimerEinstellungService {

    private final IntervallEinstellungRepository repository;

    public TimerEinstellungService(IntervallEinstellungRepository repository) {
        this.repository = repository;
    }

    /** Liest die Einstellung des Benutzers; leer, wenn noch keine gespeichert wurde. */
    public Optional<IntervallEinstellung> lese(BenutzerId benutzer) {
        Objects.requireNonNull(benutzer, "benutzer");
        return repository.findeFuer(benutzer);
    }

    /**
     * Liest die wirksame Einstellung des Benutzers (E20): die gespeicherte
     * ({@code gespeichert = true}) oder, falls keine existiert, den Standard
     * {@link IntervallEinstellung#STANDARD} ({@code gespeichert = false}).
     * Der Standard wird dabei nicht gespeichert.
     */
    public TimerEinstellungAnsicht leseOderStandard(BenutzerId benutzer) {
        return lese(benutzer)
                .map(TimerEinstellungAnsicht::gespeichert)
                .orElseGet(TimerEinstellungAnsicht::standard);
    }

    /**
     * Speichert die Einstellung des Benutzers (anlegen oder ersetzen).
     * Ein vom Client mitgesendetes {@code gespeichert} ist hier bewusst kein
     * Parameter: Es ist {@code readOnly} und wird bei {@code PUT} ignoriert (E20).
     *
     * @throws UngueltigeIntervallEinstellungException bei Werten ausserhalb der Domain-Regeln
     */
    public IntervallEinstellung speichere(BenutzerId benutzer, int trainingSekunden, int pauseSekunden) {
        Objects.requireNonNull(benutzer, "benutzer");
        IntervallEinstellung einstellung = IntervallEinstellung.ausSekunden(trainingSekunden, pauseSekunden);
        repository.speichere(benutzer, einstellung);
        return einstellung;
    }
}
