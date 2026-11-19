package de.trainingsapp.infrastructure.zeit;

import de.trainingsapp.application.zeit.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

/**
 * Produktive Systemuhr-Implementierung.
 */
@ApplicationScoped
public class SystemUhr implements Uhr {

    @Override
    public Instant jetzt() {
        return Instant.now();
    }
}
