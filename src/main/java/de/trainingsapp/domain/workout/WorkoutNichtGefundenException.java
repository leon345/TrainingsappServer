package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainNichtGefundenException;

/**
 * Ausnahme für nicht vorhandene oder nicht zugängliche Workouts (einheitlich 404 zur Vermeidung von ID-Aufzählung).
 */
public class WorkoutNichtGefundenException extends DomainNichtGefundenException {

    public static final String CODE = "WORKOUT_NICHT_GEFUNDEN";

    public WorkoutNichtGefundenException() {
        super(CODE, "Das angegebene Workout wurde nicht gefunden.");
    }
}
