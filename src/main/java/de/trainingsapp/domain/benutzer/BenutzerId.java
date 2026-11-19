package de.trainingsapp.domain.benutzer;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Benutzer-ID (UUID), gleichzeitig Basis für das 16-Byte WebAuthn User-Handle.
 */
public record BenutzerId(UUID wert) {

    public BenutzerId {
        Objects.requireNonNull(wert, "BenutzerId darf nicht null sein");
    }

    public static BenutzerId neu() {
        return new BenutzerId(UUID.randomUUID());
    }

    public static BenutzerId ausText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("BenutzerId darf nicht leer sein.");
        }
        return new BenutzerId(UUID.fromString(text));
    }

    public static BenutzerId ausUserHandle(byte[] userHandle) {
        if (userHandle == null || userHandle.length != 16) {
            throw new IllegalArgumentException("WebAuthn User-Handle muss genau 16 Bytes lang sein.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(userHandle);
        return new BenutzerId(new UUID(buffer.getLong(), buffer.getLong()));
    }

    public byte[] alsUserHandle() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(wert.getMostSignificantBits());
        buffer.putLong(wert.getLeastSignificantBits());
        return buffer.array();
    }

    public String alsText() {
        return wert.toString();
    }
}
