package de.trainingsapp.domain.platzhalter;

import java.util.Optional;
import java.util.UUID;

/**
 * Nur fuer Tests: Port (Repository-Interface) der Domain, frei von Persistenz-Technik.
 */
public interface PlatzhalterRepository {

    void speichern(Platzhalter platzhalter);

    Optional<Platzhalter> findeNachId(UUID id);
}
