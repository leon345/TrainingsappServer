package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainNichtGefundenException;

/**
 * Ausnahme für nicht vorhandene Workout-Einladungen.
 */
public class EinladungNichtGefundenException extends DomainNichtGefundenException {

    public static final String CODE = "EINLADUNG_NICHT_GEFUNDEN";

    public EinladungNichtGefundenException() {
        super(CODE, "Die angegebene Einladung wurde nicht gefunden.");
    }
}
