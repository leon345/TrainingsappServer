package de.trainingsapp.domain.benutzer;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository-Port für kurzlebige ausstehende Registrierungen.
 */
public interface AusstehendeRegistrierungRepository {

    Optional<AusstehendeRegistrierung> findeNachBenutzername(Benutzername benutzername);

    void speichern(AusstehendeRegistrierung registrierung);

    void loeschenNachBenutzerId(BenutzerId benutzerId);

    void loeschenNachBenutzername(Benutzername benutzername);

    long loescheAbgelaufene(Instant stichtag);
}
