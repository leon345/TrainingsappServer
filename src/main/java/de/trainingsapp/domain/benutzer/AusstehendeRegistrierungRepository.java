package de.trainingsapp.domain.benutzer;

import java.time.Instant;
import java.util.Optional;

/**
 * Port: Ablage der ausstehenden Registrierungen (mit Ablaufzeit). Benutzernamen werden dabei
 * wie in {@link BenutzerRepository} ohne Unterscheidung von Gross- und Kleinschreibung verglichen.
 */
public interface AusstehendeRegistrierungRepository {

    /**
     * Reserviert den Benutzernamen fuer eine Registrierung. Gibt es fuer den Namen schon eine
     * gueltige Reservierung, bleibt sie unveraendert und wird zurueckgegeben (gleicher User-Handle
     * fuer parallele oder wiederholte Versuche); sonst wird {@code neu} gespeichert. Abgelaufene
     * Reservierungen (aller Namen) werden dabei entfernt.
     *
     * @return die wirksame Reservierung
     */
    AusstehendeRegistrierung reserviere(AusstehendeRegistrierung neu, Instant jetzt);

    /**
     * Entfernt die Reservierung fuer den Benutzernamen und gibt sie zurueck, sofern sie um
     * {@code jetzt} noch gueltig ist. Eine abgelaufene Reservierung wird ebenfalls entfernt,
     * aber nicht zurueckgegeben. Hoechstens ein Aufrufer erhaelt dieselbe Reservierung.
     */
    Optional<AusstehendeRegistrierung> entnimm(String benutzername, Instant jetzt);
}
