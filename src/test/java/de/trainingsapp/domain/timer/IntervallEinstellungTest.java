package de.trainingsapp.domain.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.trainingsapp.domain.DomainException;
import org.junit.jupiter.api.Test;

class IntervallEinstellungTest {

    @Test
    void standardEinstellungIstDreissigSekundenTrainingUndZehnSekundenPause() {
        assertEquals(30, IntervallEinstellung.STANDARD.trainingSekunden());
        assertEquals(10, IntervallEinstellung.STANDARD.pauseSekunden());
    }

    @Test
    void gueltigeGrenzwerteWerdenAkzeptiert() {
        IntervallEinstellung min = new IntervallEinstellung(1, 0);
        assertEquals(1, min.trainingSekunden());
        assertEquals(0, min.pauseSekunden());

        IntervallEinstellung max = new IntervallEinstellung(86_400, 86_400);
        assertEquals(86_400, max.trainingSekunden());
        assertEquals(86_400, max.pauseSekunden());
    }

    @Test
    void ungueltigeZeitdauernWerdenAbgelehnt() {
        DomainException trainingZuKlein = assertThrows(
                DomainException.class,
                () -> new IntervallEinstellung(0, 10)
        );
        assertEquals(IntervallEinstellung.FEHLERCODE, trainingZuKlein.code());

        assertThrows(DomainException.class, () -> new IntervallEinstellung(-5, 10));
        assertThrows(DomainException.class, () -> new IntervallEinstellung(86_401, 10));
        assertThrows(DomainException.class, () -> new IntervallEinstellung(30, -1));
        assertThrows(DomainException.class, () -> new IntervallEinstellung(30, 86_401));
    }
}
