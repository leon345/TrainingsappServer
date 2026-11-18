package de.trainingsapp.domain.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.util.Optional;

/**
 * Port: Ablage der Intervalltimer-Einstellung. Je Benutzer gibt es genau eine
 * Einstellung; der Benutzer ist daher der Schluessel.
 *
 * <p>Umsetzungen liegen ausschliesslich in {@code infrastructure}
 * (derzeit In-Memory, Ziel laut S-12: PostgreSQL mit Hibernate ORM/Panache).
 */
public interface IntervallEinstellungRepository {

    /** Liefert die gespeicherte Einstellung des Benutzers, falls vorhanden. */
    Optional<IntervallEinstellung> findeFuer(BenutzerId benutzer);

    /** Legt die Einstellung des Benutzers an oder ersetzt sie (Upsert). */
    void speichere(BenutzerId benutzer, IntervallEinstellung einstellung);
}
