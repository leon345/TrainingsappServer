package de.trainingsapp.application.passkey;

/** Passkey existiert nicht oder gehoert einem anderen Benutzer. */
public class PasskeyNichtGefunden extends RuntimeException {

    public PasskeyNichtGefunden(String message) {
        super(message);
    }

    public PasskeyNichtGefunden(String message, Throwable cause) {
        super(message, cause);
    }
}
