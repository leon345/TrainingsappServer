package de.trainingsapp.infrastructure.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.infrastructure.persistenz.MitDatenbank;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Datenbank-Adapter fuer Benutzer gegen PostgreSQL (V2). */
@QuarkusTest
@TestProfile(MitDatenbank.class)
@DatenbankErforderlich
class PanacheBenutzerRepositoryTest {

    @Inject
    BenutzerRepository repository;

    @Test
    void portWirdVomDatenbankAdapterUmgesetzt() {
        // Kein In-Memory-Adapter mehr: der Port wird ausschliesslich von der Datenbank umgesetzt.
        assertInstanceOf(PanacheBenutzerRepository.class, repository);
    }

    @Test
    void anlegenUndLesenNachIdUndName() {
        Benutzer benutzer = new Benutzer(BenutzerId.neu(), eindeutig("Greta"));

        assertTrue(repository.legeAn(benutzer));

        assertEquals(Optional.of(benutzer), repository.findeNachId(benutzer.id()));
        assertEquals(Optional.of(benutzer), repository.findeNachName(benutzer.benutzername()));
        assertEquals(Optional.empty(), repository.findeNachId(BenutzerId.neu()));
        assertEquals(Optional.empty(), repository.findeNachName(eindeutig("niemand")));
    }

    @Test
    void nameIstOhneGrossUndKleinschreibungEindeutig() {
        String name = eindeutig("Hanna");
        Benutzer erster = new Benutzer(BenutzerId.neu(), name);
        assertTrue(repository.legeAn(erster));

        assertEquals(Optional.of(erster), repository.findeNachName(name.toLowerCase()));
        assertEquals(Optional.of(erster), repository.findeNachName(name.toUpperCase()));
        assertFalse(repository.legeAn(new Benutzer(BenutzerId.neu(), name)));
        assertFalse(repository.legeAn(new Benutzer(BenutzerId.neu(), name.toUpperCase())));
        assertEquals(Optional.of(erster), repository.findeNachName(name));
    }

    @Test
    void gleicheIdWirdNichtZweimalAngelegt() {
        BenutzerId id = BenutzerId.neu();
        assertTrue(repository.legeAn(new Benutzer(id, eindeutig("ida"))));
        assertFalse(repository.legeAn(new Benutzer(id, eindeutig("ida"))));
    }

    private static String eindeutig(String praefix) {
        return praefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
