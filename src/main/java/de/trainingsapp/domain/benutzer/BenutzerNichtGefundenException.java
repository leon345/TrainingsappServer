package de.trainingsapp.domain.benutzer;

import de.trainingsapp.domain.DomainNichtGefundenException;

/**
 * Ausnahme für nicht vorhandene Benutzerkonten.
 */
public class BenutzerNichtGefundenException extends DomainNichtGefundenException {

    public static final String CODE = "BENUTZER_NICHT_GEFUNDEN";

    public BenutzerNichtGefundenException() {
        super(CODE, "Der angegebene Benutzer wurde nicht gefunden.");
    }
}
