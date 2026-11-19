package de.trainingsapp.domain.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.time.Instant;
import java.util.Optional;

/**
 * Repository-Port für benutzerspezifische Intervalltimer-Einstellungen.
 */
public interface IntervallEinstellungRepository {

    Optional<IntervallEinstellung> findeNachBenutzerId(BenutzerId benutzerId);

    void speichern(BenutzerId benutzerId, IntervallEinstellung einstellung, Instant geaendertAm);
}
