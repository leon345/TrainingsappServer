package de.trainingsapp.domain.benutzer;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Repository-Port für Benutzerkonten.
 */
public interface BenutzerRepository {

    Optional<Benutzer> findeNachId(BenutzerId id);

    Map<BenutzerId, Benutzer> findeAlleNachIds(Collection<BenutzerId> ids);

    Optional<Benutzer> findeNachBenutzername(Benutzername benutzername);

    boolean existiertBenutzername(Benutzername benutzername);

    void speichern(Benutzer benutzer);

    void aktualisieren(Benutzer benutzer);

    boolean loeschen(BenutzerId id);

    int loescheKontenOhnePasskeyVor(Instant stichtag);
}
