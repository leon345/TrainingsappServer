package de.trainingsapp.domain.benutzer;

import java.time.Instant;
import java.util.Objects;

/**
 * Eine begonnene, noch nicht abgeschlossene Registrierung: Zwischen dem Abruf der
 * Registrierungsoptionen und dem Abschluss ist fuer den Benutzernamen eine {@link BenutzerId}
 * vorgesehen. Sie wird als User-Handle an den Authenticator gegeben und muss deshalb beim
 * Abschluss dieselbe sein. Die Reservierung verfaellt nach {@code gueltigBis} (TTL, E18).
 */
public record AusstehendeRegistrierung(BenutzerId vorgesehen, String benutzername, Instant gueltigBis) {

    public AusstehendeRegistrierung {
        Objects.requireNonNull(vorgesehen, "vorgesehen");
        Objects.requireNonNull(benutzername, "benutzername");
        Objects.requireNonNull(gueltigBis, "gueltigBis");
    }

    /** Gueltig bis ausschliesslich {@code gueltigBis}. */
    public boolean istGueltigUm(Instant zeitpunkt) {
        return zeitpunkt.isBefore(gueltigBis);
    }
}
