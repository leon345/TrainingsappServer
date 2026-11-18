package de.trainingsapp.application.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.timer.IntervallEinstellung;
import org.junit.jupiter.api.Test;

class TimerEinstellungAnsichtTest {

    @Test
    void standardAnsichtIstNichtGespeichert() {
        TimerEinstellungAnsicht ansicht = TimerEinstellungAnsicht.standard();
        assertEquals(IntervallEinstellung.STANDARD, ansicht.einstellung());
        assertFalse(ansicht.gespeichert());
    }

    @Test
    void gespeicherteAnsichtIstGespeichert() {
        IntervallEinstellung e = IntervallEinstellung.ausSekunden(45, 15);
        TimerEinstellungAnsicht ansicht = TimerEinstellungAnsicht.gespeichert(e);
        assertEquals(e, ansicht.einstellung());
        assertTrue(ansicht.gespeichert());
    }

    @Test
    void einstellungIstPflicht() {
        assertThrows(NullPointerException.class, () -> new TimerEinstellungAnsicht(null, false));
    }
}
