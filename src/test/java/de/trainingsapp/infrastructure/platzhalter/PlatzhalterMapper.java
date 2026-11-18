package de.trainingsapp.infrastructure.platzhalter;

import de.trainingsapp.domain.platzhalter.Platzhalter;

/**
 * Nur fuer Tests: Mapping zwischen Domain-Objekt und JPA-Entity.
 */
final class PlatzhalterMapper {

    private PlatzhalterMapper() {
    }

    static PlatzhalterEntity zuEntity(Platzhalter platzhalter) {
        PlatzhalterEntity entity = new PlatzhalterEntity();
        entity.id = platzhalter.id();
        entity.name = platzhalter.name();
        return entity;
    }

    static Platzhalter zuDomain(PlatzhalterEntity entity) {
        return new Platzhalter(entity.id, entity.name);
    }
}
