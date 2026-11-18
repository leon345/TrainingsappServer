package de.trainingsapp.application.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Prueft die CDI-Verdrahtung: Service wird mit dem In-Memory-Adapter als Port-Umsetzung erzeugt.
 */
@QuarkusTest
class TimerEinstellungServiceCdiTest {

    @Inject
    TimerEinstellungService service;

    @Test
    void serviceIstMitAdapterVerdrahtet() {
        BenutzerId benutzer = BenutzerId.neu();
        service.speichere(benutzer, 30, 10);
        assertEquals(30, service.lese(benutzer).orElseThrow().trainingSekunden());
    }
}
