package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.DomainNichtGefundenException;

/**
 * Ausnahme für nicht vorhandene oder nicht zugängliche Passkeys (liefert einheitlich 404 zur Vermeidung von ID-Aufzählung).
 */
public class PasskeyNichtGefundenException extends DomainNichtGefundenException {

    public static final String CODE = "PASSKEY_NICHT_GEFUNDEN";

    public PasskeyNichtGefundenException() {
        super(CODE, "Der angegebene Passkey wurde nicht gefunden.");
    }
}
