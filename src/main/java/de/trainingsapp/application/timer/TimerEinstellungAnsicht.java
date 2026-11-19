package de.trainingsapp.application.timer;

import de.trainingsapp.domain.timer.IntervallEinstellung;
import java.util.Objects;

/**
 * Ergebnisansicht einer Intervalltimer-Abfrage mit Kennzeichen, ob bereits ein gespeicherter Eintrag vorliegt.
 */
public record TimerEinstellungAnsicht(IntervallEinstellung einstellung, boolean gespeichert) {

    public TimerEinstellungAnsicht {
        Objects.requireNonNull(einstellung, "einstellung darf nicht null sein");
    }
}
