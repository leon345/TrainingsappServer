package de.trainingsapp.domain.passkey;

import java.util.Objects;

/** WebAuthn-Credential-ID (Base64URL ohne Padding), global eindeutig. */
public record PasskeyId(String wert) {

    public PasskeyId {
        Objects.requireNonNull(wert, "wert");
        if (wert.isBlank()) {
            throw new IllegalArgumentException("PasskeyId darf nicht leer sein");
        }
    }

    @Override
    public String toString() {
        return wert;
    }
}
