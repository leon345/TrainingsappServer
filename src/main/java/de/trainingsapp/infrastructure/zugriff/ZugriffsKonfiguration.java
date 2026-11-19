package de.trainingsapp.infrastructure.zugriff;

import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Stellt die framework-freie ZugriffsPolicy als CDI-Bean bereit.
 */
@ApplicationScoped
public class ZugriffsKonfiguration {

    @Produces
    @ApplicationScoped
    public ZugriffsPolicy zugriffsPolicy() {
        return new ZugriffsPolicy();
    }
}
