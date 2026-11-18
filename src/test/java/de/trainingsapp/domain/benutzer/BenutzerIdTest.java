package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BenutzerIdTest {

    private static final String TEXT = "3f2c1a9e-8b7d-4c6e-9f01-23456789abcd";

    @Test
    void gleicheWerteSindGleich() {
        assertEquals(new BenutzerId(UUID.fromString(TEXT)), new BenutzerId(UUID.fromString(TEXT)));
        assertEquals(new BenutzerId(UUID.fromString(TEXT)), BenutzerId.aus(TEXT));
    }

    @Test
    void fehlendeKennungWirdAbgewiesen() {
        assertThrows(NullPointerException.class, () -> new BenutzerId(null));
        assertThrows(NullPointerException.class, () -> BenutzerId.aus(null));
    }

    @Test
    void textformIstDieKanonischeUuid() {
        assertEquals(TEXT, BenutzerId.aus(TEXT).toString());
        assertEquals(BenutzerId.aus(TEXT), BenutzerId.aus(TEXT.toUpperCase()));
    }

    @Test
    void ungueltigeOderNichtKanonischeTexteWerdenAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.aus(""));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.aus(" "));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.aus("anna"));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.aus("1-2-3-4-5"));
        assertThrows(IllegalArgumentException.class, () -> BenutzerId.aus(TEXT + " "));
    }

    @Test
    void neueKennungenSindVerschieden() {
        assertNotEquals(BenutzerId.neu(), BenutzerId.neu());
    }
}
