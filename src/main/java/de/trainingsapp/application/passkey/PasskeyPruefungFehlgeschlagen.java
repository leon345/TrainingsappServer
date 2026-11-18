package de.trainingsapp.application.passkey;

/** Passkey-Antwort ungueltig oder Challenge fehlt/abgelaufen. */
public class PasskeyPruefungFehlgeschlagen extends RuntimeException {

    public PasskeyPruefungFehlgeschlagen(String message) {
        super(message);
    }

    public PasskeyPruefungFehlgeschlagen(String message, Throwable cause) {
        super(message, cause);
    }
}
