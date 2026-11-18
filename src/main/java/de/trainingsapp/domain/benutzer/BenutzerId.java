package de.trainingsapp.domain.benutzer;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object: fachliche Kennung eines Benutzers (E18).
 *
 * <p>Eine zufaellige UUID ohne Personenbezug. Sie dient zugleich als WebAuthn-User-Handle
 * (16 Rohbytes, siehe {@code infrastructure.passkey.UserHandle}) und als Principal der Sitzung.
 */
public record BenutzerId(UUID wert) {

    public BenutzerId {
        Objects.requireNonNull(wert, "wert");
    }

    /** Vergibt eine neue, zufaellige Kennung. */
    public static BenutzerId neu() {
        return new BenutzerId(UUID.randomUUID());
    }

    /**
     * Liest die kanonische Textform ({@code 8-4-4-4-12} Hexziffern).
     *
     * @throws IllegalArgumentException wenn {@code text} keine kanonische UUID ist
     */
    public static BenutzerId aus(String text) {
        Objects.requireNonNull(text, "text");
        UUID uuid;
        try {
            uuid = UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Keine gueltige BenutzerId: " + text, e);
        }
        // UUID.fromString akzeptiert auch verkuerzte Formen wie "1-2-3-4-5"; die lassen wir nicht zu.
        if (!uuid.toString().equals(text.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Keine kanonische BenutzerId: " + text);
        }
        return new BenutzerId(uuid);
    }

    @Override
    public String toString() {
        return wert.toString();
    }
}
