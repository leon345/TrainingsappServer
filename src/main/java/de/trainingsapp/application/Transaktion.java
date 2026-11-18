package de.trainingsapp.application;

import java.util.function.Supplier;

/**
 * Port: Transaktionsgrenze aus Sicht der Use-Cases (E26). Alles, was innerhalb laeuft, wird
 * gemeinsam festgeschrieben oder bei einer Ausnahme vollstaendig zurueckgerollt. Laeuft bereits
 * eine Transaktion, wird sie mitbenutzt.
 *
 * <p>Regel (E26): Externe Aufrufe (WebAuthn-Pruefung, Challenge-Verbrauch, Sitzung, Rate-Limit)
 * liegen nie innerhalb. Die Schreibmethoden der Datenbank-Adapter verlangen eine laufende
 * Transaktion ({@code MANDATORY}) und lassen sich daher nicht versehentlich ohne diesen Port aufrufen.
 */
public interface Transaktion {

    <T> T ausfuehren(Supplier<T> arbeit);

    void ausfuehren(Runnable arbeit);
}
