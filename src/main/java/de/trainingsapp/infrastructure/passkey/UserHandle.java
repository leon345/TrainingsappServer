package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * WebAuthn-User-Handle (E18): die 16 Rohbytes der UUID einer {@link BenutzerId}
 * (big-endian, hoeherwertige 64 Bit zuerst). Enthaelt keine personenbezogenen Daten
 * und liegt weit unter der Obergrenze von 64 Byte (WebAuthn L3, 5.4.3).
 */
final class UserHandle {

    static final int LAENGE = 16;

    private UserHandle() {
    }

    static byte[] zuBytes(BenutzerId id) {
        Objects.requireNonNull(id, "id");
        UUID uuid = id.wert();
        return ByteBuffer.allocate(LAENGE)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    static BenutzerId ausBytes(byte[] handle) {
        Objects.requireNonNull(handle, "handle");
        if (handle.length != LAENGE) {
            throw new IllegalArgumentException("User-Handle muss genau " + LAENGE + " Byte lang sein, ist "
                    + handle.length);
        }
        ByteBuffer puffer = ByteBuffer.wrap(handle);
        return new BenutzerId(new UUID(puffer.getLong(), puffer.getLong()));
    }
}
