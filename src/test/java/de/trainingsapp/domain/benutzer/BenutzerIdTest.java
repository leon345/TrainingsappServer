package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BenutzerIdTest {

    @Test
    void gleicheWerteSindGleich() {
        assertEquals(new BenutzerId("anna"), new BenutzerId("anna"));
    }

    @Test
    void leereOderFehlendeKennungWirdAbgewiesen() {
        assertThrows(NullPointerException.class, () -> new BenutzerId(null));
        assertThrows(IllegalArgumentException.class, () -> new BenutzerId(" "));
    }
}
