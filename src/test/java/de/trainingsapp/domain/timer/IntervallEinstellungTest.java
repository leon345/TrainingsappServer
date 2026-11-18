package de.trainingsapp.domain.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntervallEinstellungTest {

    @Test
    void gueltigeEinstellungInSekunden() {
        IntervallEinstellung e = IntervallEinstellung.ausSekunden(45, 15);
        assertEquals(45, e.trainingSekunden());
        assertEquals(15, e.pauseSekunden());
    }

    @Test
    void gueltigeEinstellungInMinutenUndSekunden() {
        IntervallEinstellung e = IntervallEinstellung.ausMinutenUndSekunden(1, 30, 0, 45);
        assertEquals(90, e.trainingSekunden());
        assertEquals(45, e.pauseSekunden());
    }

    @Test
    void pauseNullIstErlaubt() {
        assertEquals(0, IntervallEinstellung.ausSekunden(30, 0).pauseSekunden());
    }

    @Test
    void grenzwerteSindErlaubt() {
        IntervallEinstellung e = new IntervallEinstellung(IntervallEinstellung.MIN_TRAINING, IntervallEinstellung.MAX_DAUER);
        assertEquals(1, e.trainingSekunden());
        assertEquals(86_400, e.pauseSekunden());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",   // negative Trainingsphase
            "10, -1",   // negative Pause
            "0, 10",    // Trainingsphase 0
            "86401, 10", // Trainingsphase ueber Maximum
            "10, 86401"  // Pause ueber Maximum
    })
    void ungueltigeSekundenwerteWerdenAbgewiesen(long training, long pause) {
        assertThrows(UngueltigeIntervallEinstellungException.class,
                () -> IntervallEinstellung.ausSekunden(training, pause));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 0, 0, 10",  // negative Minuten Training
            "0, -5, 0, 10",  // negative Sekunden Training
            "1, 0, -1, 0",   // negative Minuten Pause
            "1, 60, 0, 10",  // Sekundenanteil > 59
            "1, 0, 0, 60",   // Sekundenanteil Pause > 59
            "1441, 0, 0, 0"  // ueber 24 h
    })
    void ungueltigeMinutenwerteWerdenAbgewiesen(long tMin, long tSek, long pMin, long pSek) {
        assertThrows(UngueltigeIntervallEinstellungException.class,
                () -> IntervallEinstellung.ausMinutenUndSekunden(tMin, tSek, pMin, pSek));
    }

    @Test
    void bruchteileVonSekundenWerdenAbgewiesen() {
        assertThrows(UngueltigeIntervallEinstellungException.class,
                () -> new IntervallEinstellung(Duration.ofMillis(1500), Duration.ZERO));
    }

    @Test
    void nullWirdAbgewiesen() {
        assertThrows(NullPointerException.class, () -> new IntervallEinstellung(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new IntervallEinstellung(Duration.ofSeconds(1), null));
    }
}
