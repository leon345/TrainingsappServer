package de.trainingsapp.domain.benutzer;

import java.util.Optional;

/** Port: Ablage der Benutzer. */
public interface BenutzerRepository {

    Optional<Benutzer> findeNachId(BenutzerId id);

    Optional<Benutzer> findeNachName(Benutzername benutzername);

    /**
     * Legt den Benutzer an.
     *
     * @return {@code false}, wenn der Benutzername bereits vergeben ist
     */
    boolean legeAn(Benutzer benutzer);

    /**
     * Vergibt die naechste Passkey-Nummer des Benutzers fuer die automatische Bezeichnung
     * "Passkey n" (E29). Die Nummern steigen streng und werden nie wiederverwendet, auch nicht nach
     * Loeschen oder Umbenennen. Nur innerhalb einer Transaktion; ein Rollback gibt die Nummer frei.
     */
    int naechstePasskeyNummer(BenutzerId id);
}
