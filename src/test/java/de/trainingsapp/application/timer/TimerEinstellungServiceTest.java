package de.trainingsapp.application.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.UngueltigeIntervallEinstellungException;
import de.trainingsapp.infrastructure.timer.InMemoryIntervallEinstellungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimerEinstellungServiceTest {

    private static final BenutzerId ANNA = BenutzerId.aus("00000000-0000-4000-8000-00000000a001");
    private static final BenutzerId BEN = BenutzerId.aus("00000000-0000-4000-8000-00000000b002");

    private TimerEinstellungService service;

    @BeforeEach
    void setUp() {
        service = new TimerEinstellungService(new InMemoryIntervallEinstellungRepository());
    }

    @Test
    void ohneGespeicherteEinstellungIstErgebnisLeer() {
        assertTrue(service.lese(ANNA).isEmpty());
    }

    @Test
    void gespeicherteEinstellungKannGelesenWerden() {
        IntervallEinstellung gespeichert = service.speichere(ANNA, 45, 15);

        assertEquals(45, gespeichert.trainingSekunden());
        assertEquals(15, gespeichert.pauseSekunden());
        assertEquals(gespeichert, service.lese(ANNA).orElseThrow());
    }

    @Test
    void erneutesSpeichernErsetztDieEinstellung() {
        service.speichere(ANNA, 45, 15);
        service.speichere(ANNA, 60, 30);

        IntervallEinstellung gelesen = service.lese(ANNA).orElseThrow();
        assertEquals(60, gelesen.trainingSekunden());
        assertEquals(30, gelesen.pauseSekunden());
    }

    @Test
    void einstellungenSindJeBenutzerGetrennt() {
        service.speichere(ANNA, 45, 15);
        service.speichere(BEN, 20, 10);

        assertEquals(45, service.lese(ANNA).orElseThrow().trainingSekunden());
        assertEquals(20, service.lese(BEN).orElseThrow().trainingSekunden());
    }

    @Test
    void ungueltigeWerteWerdenAbgewiesenUndNichtGespeichert() {
        service.speichere(ANNA, 45, 15);

        assertThrows(UngueltigeIntervallEinstellungException.class, () -> service.speichere(ANNA, -1, 15));
        assertThrows(UngueltigeIntervallEinstellungException.class, () -> service.speichere(ANNA, 45, -1));
        assertThrows(UngueltigeIntervallEinstellungException.class, () -> service.speichere(ANNA, 0, 15));

        assertEquals(45, service.lese(ANNA).orElseThrow().trainingSekunden());
    }

    @Test
    void benutzerIstPflicht() {
        assertThrows(NullPointerException.class, () -> service.lese(null));
        assertThrows(NullPointerException.class, () -> service.speichere(null, 45, 15));
    }
}
