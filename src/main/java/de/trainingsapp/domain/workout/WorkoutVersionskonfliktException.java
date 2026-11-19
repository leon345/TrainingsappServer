package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainKonfliktException;

/**
 * Ausnahme bei einem Konflikt der optimistischen Sperre beim Aktualisieren eines Workouts.
 */
public class WorkoutVersionskonfliktException extends DomainKonfliktException {

    public static final String CODE = "WORKOUT_VERSIONSKONFLIKT";

    public WorkoutVersionskonfliktException() {
        super(CODE, "Das Workout wurde zwischenzeitlich geändert. Bitte lade den aktuellen Stand neu.");
    }
}
