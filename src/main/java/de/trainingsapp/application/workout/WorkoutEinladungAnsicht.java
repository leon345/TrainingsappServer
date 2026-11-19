package de.trainingsapp.application.workout;

import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import java.util.Objects;

/**
 * Projektion einer Workout-Einladung inkl. Benutzername des eingeladenen Benutzers.
 */
public record WorkoutEinladungAnsicht(
        WorkoutEinladung einladung,
        Benutzername eingeladenerBenutzername
) {
    public WorkoutEinladungAnsicht {
        Objects.requireNonNull(einladung, "einladung darf nicht null sein");
        Objects.requireNonNull(eingeladenerBenutzername, "eingeladenerBenutzername darf nicht null sein");
    }
}
