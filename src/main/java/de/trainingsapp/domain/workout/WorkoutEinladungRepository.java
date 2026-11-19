package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.util.Collection;
import java.util.List;

/**
 * Repository-Port für Workout-Einladungen.
 */
public interface WorkoutEinladungRepository {

    List<WorkoutEinladung> findeAlleNachWorkoutId(WorkoutId workoutId);

    List<WorkoutEinladung> findeAlleNachWorkoutIds(Collection<WorkoutId> workoutIds);

    List<WorkoutEinladung> findeAlleNachEingeladenerId(BenutzerId eingeladenerId);

    boolean existiertAktiveEinladung(WorkoutId workoutId, BenutzerId eingeladenerId);

    void speichern(WorkoutEinladung einladung);

    boolean loeschenNachWorkoutUndEingeladener(WorkoutId workoutId, BenutzerId eingeladenerId);
}
