package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BenutzerIdTest {

    @Test
    void rundreiseUserHandleMit16BytesBleibtIdentisch() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BenutzerId id = new BenutzerId(uuid);

        byte[] handle = id.alsUserHandle();
        assertEquals(16, handle.length);
        assertEquals(id, BenutzerId.ausUserHandle(handle));
        assertEquals(id, BenutzerId.ausText(id.alsText()));
    }

    @Test
    void ungultigeHandleLaengeWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.ausUserHandle(null));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.ausUserHandle(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.ausUserHandle(new byte[17]));
    }
}
