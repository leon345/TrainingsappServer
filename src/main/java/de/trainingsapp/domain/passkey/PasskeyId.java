package de.trainingsapp.domain.passkey;

import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Kennung eines gespeicherten Passkeys.
 */
public record PasskeyId(UUID wert) {

    public PasskeyId {
        Objects.requireNonNull(wert, "PasskeyId darf nicht null sein");
    }

    public static PasskeyId neu() {
        return new PasskeyId(UUID.randomUUID());
    }

    public static PasskeyId ausText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("PasskeyId darf nicht leer sein.");
        }
        return new PasskeyId(UUID.fromString(text));
    }

    public String alsText() {
        return wert.toString();
    }
}
