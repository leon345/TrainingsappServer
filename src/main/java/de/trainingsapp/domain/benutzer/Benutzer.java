package de.trainingsapp.domain.benutzer;

import java.util.Objects;

/**
 * Benutzer: Kennung plus Benutzername (Pseudonym, E17/E28), der dem Authenticator bei der
 * Passkey-Registrierung angezeigt wird. Weitere Attribute (Einwilligung, Rollen) folgen mit S-08 bzw. S-05.
 */
public record Benutzer(BenutzerId id, Benutzername benutzername) {

    public Benutzer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(benutzername, "benutzername");
    }
}
