package de.trainingsapp.application.workout;

import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.util.List;

/**
 * Eingabedaten zum Erstellen oder Aktualisieren eines Workouts.
 */
public record WorkoutEingabe(
        WorkoutTyp typ,
        String titel,
        Instant startZeit,
        Integer dauerSekunden,
        String notiz,
        Sichtbarkeit sichtbarkeit,
        List<UebungEingabe> uebungen
) {
}
