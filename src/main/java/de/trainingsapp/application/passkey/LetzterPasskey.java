package de.trainingsapp.application.passkey;

/** Der letzte Passkey eines Benutzers kann nicht geloescht werden. */
public class LetzterPasskey extends RuntimeException {

    public LetzterPasskey(String message) {
        super(message);
    }

    public LetzterPasskey(String message, Throwable cause) {
        super(message, cause);
    }
}
