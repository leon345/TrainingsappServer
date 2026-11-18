package de.trainingsapp.application.passkey;

/** Benutzername ist bereits vergeben. */
public class BenutzernameVergeben extends RuntimeException {

    public BenutzernameVergeben(String message) {
        super(message);
    }

    public BenutzernameVergeben(String message, Throwable cause) {
        super(message, cause);
    }
}
