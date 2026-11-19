package de.trainingsapp.domain.benutzer;

import java.util.Optional;

/**
 * Repository-Port für Benutzerkonten.
 */
public interface BenutzerRepository {

    Optional<Benutzer> findeNachId(BenutzerId id);

    Optional<Benutzer> findeNachBenutzername(Benutzername benutzername);

    boolean existiertBenutzername(Benutzername benutzername);

    void speichern(Benutzer benutzer);

    void aktualisieren(Benutzer benutzer);
}
