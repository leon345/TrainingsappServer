package de.trainingsapp.domain.benutzer;

import de.trainingsapp.domain.DomainException;

/**
 * Ausnahme für ungültige oder gesperrte Benutzernamen.
 */
public class UngueltigerBenutzernameException extends DomainException {

    public static final String CODE = "UNGUELTIGER_BENUTZERNAME";

    public UngueltigerBenutzernameException(String meldung) {
        super(CODE, meldung);
    }
}
