package de.trainingsapp.infrastructure.zeit;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Systemuhr (UTC) als CDI-Bean; Tests ersetzen sie durch eine stellbare Uhr. */
@ApplicationScoped
public class Uhr {

    @Produces
    @Singleton
    @DefaultBean
    Clock systemuhr() {
        return Clock.systemUTC();
    }
}
