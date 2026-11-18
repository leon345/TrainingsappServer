package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port: Ablage der Passkeys. Schreibende Methoden laufen innerhalb einer Transaktion (E26). */
public interface PasskeyRepository {

    Optional<Passkey> findeNachId(PasskeyId id);

    /** Passkeys des Benutzers, aelteste zuerst (am selben Tag nach Credential-ID). */
    List<Passkey> findeFuer(BenutzerId benutzerId);

    /**
     * Speichert einen neuen Passkey.
     *
     * @return {@code false}, wenn die Credential-ID bereits existiert
     */
    boolean fuegeHinzu(Passkey passkey);

    /**
     * Vermerkt eine Anmeldung: schreibt den Signaturzaehler, wenn er sich geaendert hat, und
     * {@code zuletzt_verwendet_am}, wenn sich der Tag geaendert hat (hoechstens einmal pro Tag, E29).
     */
    void vermerkeAnmeldung(PasskeyId id, long zaehler, LocalDate tag);

    /**
     * Benennt einen eigenen Passkey um.
     *
     * @return {@code false}, wenn es keinen Passkey mit dieser ID gibt, der dem Benutzer gehoert
     */
    boolean benenneUm(BenutzerId benutzerId, PasskeyId id, PasskeyBezeichnung bezeichnung);

    /**
     * Loescht den Passkey des Benutzers, sofern danach mindestens einer uebrig bleibt.
     * Pruefung und Loeschen erfolgen atomar (Schutz vor parallelen Loeschungen).
     */
    LoeschErgebnis loescheWennNichtLetzter(BenutzerId benutzerId, PasskeyId id);

    enum LoeschErgebnis { GELOESCHT, NICHT_GEFUNDEN, LETZTER_PASSKEY }
}
