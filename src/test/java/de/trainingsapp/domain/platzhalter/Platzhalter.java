package de.trainingsapp.domain.platzhalter;

import java.util.Objects;
import java.util.UUID;

/**
 * Nur fuer Tests: Platzhalter-Domain-Objekt, an dem das Persistenzmuster
 * (Port in domain, Panache-Adapter und Mapping in infrastructure) geprueft wird.
 * Fachliche Modelle folgen mit S-03.
 */
public record Platzhalter(UUID id, String name) {

    public Platzhalter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
