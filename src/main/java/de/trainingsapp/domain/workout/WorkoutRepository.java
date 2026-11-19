package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.util.List;
import java.util.Optional;

/**
 * Repository-Port für Workouts inkl. Übungen und Sätzen.
 */
public interface WorkoutRepository {

    Optional<Workout> findeNachId(WorkoutId id);

    List<Workout> findeAlleNachOwner(BenutzerId ownerId, WorkoutTyp optionalerTyp);

    Optional<Workout> findeNachOwnerUndHerkunftUndExterneId(
            BenutzerId ownerId,
            Herkunft herkunft,
            String externeId
    );

    void speichern(Workout workout);

    Workout aktualisieren(Workout aktualisiertesWorkout, long erwarteteVersion);

    boolean loeschen(WorkoutId id);
}
