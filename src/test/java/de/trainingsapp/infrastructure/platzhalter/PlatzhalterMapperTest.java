package de.trainingsapp.infrastructure.platzhalter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.domain.platzhalter.Platzhalter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Laeuft ohne Datenbank. */
class PlatzhalterMapperTest {

    @Test
    void mappingHinUndZurueckErhaeltAlleFelder() {
        Platzhalter original = new Platzhalter(UUID.randomUUID(), "Test");

        PlatzhalterEntity entity = PlatzhalterMapper.zuEntity(original);

        assertEquals(original.id(), entity.id);
        assertEquals(original.name(), entity.name);
        assertEquals(original, PlatzhalterMapper.zuDomain(entity));
    }
}
