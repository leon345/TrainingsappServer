package de.trainingsapp.application.erweiterung;

import de.trainingsapp.application.workout.UebungEingabe;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.util.List;

/**
 * Fachliche Nutzdaten für den internen Workout-Import.
 * Importierte Workouts werden stets als PRIVAT gespeichert.
 */
public record WorkoutImportDaten(
        WorkoutTyp typ,
        String titel,
        Instant startZeit,
        Integer dauerSekunden,
        String notiz,
        List<UebungEingabe> uebungen
) {
}
