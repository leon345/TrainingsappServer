package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.DomainKonfliktException;

/**
 * Ausnahme, wenn versucht wird, den letzten verbleibenden Passkey eines Kontos zu löschen.
 */
public class LetzterPasskeyException extends DomainKonfliktException {

    public static final String CODE = "LETZTER_PASSKEY";

    public LetzterPasskeyException() {
        super(CODE, "Der letzte Passkey eines Kontos darf nicht gelöscht werden.");
    }
}
