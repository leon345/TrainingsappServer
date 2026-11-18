package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.util.List;
import java.util.Optional;

/** Port: Ablage der Passkeys. */
public interface PasskeyRepository {

    Optional<Passkey> findeNachId(PasskeyId id);

    List<Passkey> findeFuer(BenutzerId benutzerId);

    /**
     * Speichert einen neuen Passkey.
     *
     * @return {@code false}, wenn die Credential-ID bereits existiert
     */
    boolean fuegeHinzu(Passkey passkey);

    void aktualisiereZaehler(PasskeyId id, long zaehler);

    /**
     * Loescht den Passkey des Benutzers, sofern danach mindestens einer uebrig bleibt.
     * Pruefung und Loeschen erfolgen atomar (Schutz vor parallelen Loeschungen).
     */
    LoeschErgebnis loescheWennNichtLetzter(BenutzerId benutzerId, PasskeyId id);

    enum LoeschErgebnis { GELOESCHT, NICHT_GEFUNDEN, LETZTER_PASSKEY }
}
