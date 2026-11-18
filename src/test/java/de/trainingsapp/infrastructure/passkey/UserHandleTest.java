package de.trainingsapp.infrastructure.passkey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class UserHandleTest {

    @Test
    void sindDie16RohbytesDerUuidInTextreihenfolge() {
        BenutzerId id = BenutzerId.aus("00112233-4455-6677-8899-aabbccddeeff");
        assertArrayEquals(HexFormat.of().parseHex("00112233445566778899aabbccddeeff"), UserHandle.zuBytes(id));
    }

    @Test
    void hinUndZurueck() {
        BenutzerId id = BenutzerId.neu();
        byte[] handle = UserHandle.zuBytes(id);
        assertEquals(16, handle.length);
        assertEquals(id, UserHandle.ausBytes(handle));
    }

    @Test
    void andereLaengenWerdenAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> UserHandle.ausBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> UserHandle.ausBytes(new byte[17]));
        assertThrows(IllegalArgumentException.class, () -> UserHandle.ausBytes(new byte[36]));
    }
}
