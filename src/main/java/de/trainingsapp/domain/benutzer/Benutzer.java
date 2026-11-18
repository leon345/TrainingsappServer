package de.trainingsapp.domain.benutzer;

import java.util.Objects;

/**
 * Minimaler Benutzer fuer den S-04-Spike: Kennung plus der Name, der dem
 * Authenticator bei der Passkey-Registrierung angezeigt wird.
 * Weitere Attribute (Einwilligung, Rollen) folgen mit S-08 bzw. S-05.
 */
public record Benutzer(BenutzerId id, String benutzername) {

    public Benutzer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(benutzername, "benutzername");
        if (benutzername.isBlank()) {
            throw new IllegalArgumentException("Benutzername darf nicht leer sein");
        }
    }
}
