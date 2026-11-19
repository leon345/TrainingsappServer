package de.trainingsapp.application.workout;

import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.workout.Workout;
import java.time.Instant;
import java.util.Objects;

/**
 * Projektion eines geteilten Workouts inkl. Besitzername und Einladungszeitpunkt.
 */
public record GeteiltesWorkoutAnsicht(
        Workout workout,
        Benutzername ownerBenutzername,
        Instant eingeladenAm
) {
    public GeteiltesWorkoutAnsicht {
        Objects.requireNonNull(workout, "workout darf nicht null sein");
        Objects.requireNonNull(ownerBenutzername, "ownerBenutzername darf nicht null sein");
        Objects.requireNonNull(eingeladenAm, "eingeladenAm darf nicht null sein");
    }
}
