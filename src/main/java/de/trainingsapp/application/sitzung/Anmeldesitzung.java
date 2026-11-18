package de.trainingsapp.application.sitzung;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.util.Optional;

/**
 * Port: Anmeldesitzung aus Sicht der Use-Cases (E23). Die Use-Cases kennen nur die
 * {@link BenutzerId}; wie die Sitzung transportiert wird (heute Cookie, spaeter z. B. ein Token
 * fuer eine native App), ist Sache des Adapters in {@code infrastructure}.
 *
 * <p>Neutrales Ergebnis: {@link #aktuellerBenutzer()} liefert die Kennung oder nichts; es gibt keine
 * Cookie-, Token- oder Security-Typen in der Signatur.
 */
public interface Anmeldesitzung {

    /** Der Benutzer der laufenden Anfrage, sofern angemeldet. */
    Optional<BenutzerId> aktuellerBenutzer();

    /** Stellt nach erfolgreicher Pruefung eine Sitzung fuer {@code benutzer} her. */
    void anmelden(BenutzerId benutzer);

    /** Beendet die Sitzung der laufenden Anfrage (ohne Wirkung, wenn keine besteht). */
    void beenden();

    /** Wie {@link #aktuellerBenutzer()}, aber mit Fehler, wenn niemand angemeldet ist. */
    default BenutzerId angemeldeterBenutzer() {
        return aktuellerBenutzer().orElseThrow(NichtAngemeldet::new);
    }
}
