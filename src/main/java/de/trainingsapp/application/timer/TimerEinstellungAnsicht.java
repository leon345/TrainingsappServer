package de.trainingsapp.application.timer;

import de.trainingsapp.domain.timer.IntervallEinstellung;

import java.util.Objects;

/**
 * Ergebnis des Lesens einer Timer-Einstellung (E20): die wirksame Einstellung und
 * ob sie vom Benutzer gespeichert wurde ({@code true}) oder der Standard
 * {@link IntervallEinstellung#STANDARD} ist ({@code false}).
 *
 * <p>Die API-Schicht (S-02b) bildet {@code gespeichert} 1:1 auf das
 * {@code readOnly}-Feld {@code gespeichert} von {@code TimerEinstellungV1} ab.
 */
public record TimerEinstellungAnsicht(IntervallEinstellung einstellung, boolean gespeichert) {

    public TimerEinstellungAnsicht {
        Objects.requireNonNull(einstellung, "einstellung");
    }

    static TimerEinstellungAnsicht gespeichert(IntervallEinstellung einstellung) {
        return new TimerEinstellungAnsicht(einstellung, true);
    }

    static TimerEinstellungAnsicht standard() {
        return new TimerEinstellungAnsicht(IntervallEinstellung.STANDARD, false);
    }
}
