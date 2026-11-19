package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.time.Instant;
import java.util.Objects;

/**
 * Explizite Lese-Einladung eines Benutzers zu einem Workout.
 */
public record WorkoutEinladung(
        WorkoutEinladungId id,
        WorkoutId workoutId,
        BenutzerId eingeladenerId,
        WorkoutEinladungStatus status,
        Instant erstelltAm
) {

    public WorkoutEinladung {
        Objects.requireNonNull(id, "WorkoutEinladungId darf nicht null sein");
        Objects.requireNonNull(workoutId, "workoutId darf nicht null sein");
        Objects.requireNonNull(eingeladenerId, "eingeladenerId darf nicht null sein");
        Objects.requireNonNull(status, "status darf nicht null sein");
        Objects.requireNonNull(erstelltAm, "erstelltAm darf nicht null sein");
    }

    public static WorkoutEinladung aktiv(WorkoutId workoutId, BenutzerId eingeladenerId, Instant jetzt) {
        return new WorkoutEinladung(
                WorkoutEinladungId.neu(),
                workoutId,
                eingeladenerId,
                WorkoutEinladungStatus.AKTIV,
                jetzt
        );
    }

    public boolean istAktiv() {
        return status == WorkoutEinladungStatus.AKTIV;
    }
}
