package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.util.List;
import java.util.Optional;

/**
 * Repository-Port für gespeicherte Passkeys.
 */
public interface PasskeyRepository {

    Optional<Passkey> findeNachId(PasskeyId id);

    Optional<Passkey> findeNachCredentialId(String credentialId);

    List<Passkey> findeAlleNachBenutzerId(BenutzerId benutzerId);

    long zaehleNachBenutzerId(BenutzerId benutzerId);

    void speichern(Passkey passkey);

    void aktualisieren(Passkey passkey);

    boolean loeschen(PasskeyId id);
}
