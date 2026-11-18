package de.trainingsapp.domain.benutzer;

import java.time.Instant;
import java.util.Optional;

/**
 * Port: Ablage der ausstehenden Registrierungen (mit Ablaufzeit), je Benutzername hoechstens eine.
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
     * Haelt die fuer diese Reservierung ausgegebene Challenge fest; eine fruehere wird ersetzt.
     * Nur Antworten auf diese Challenge koennen die Registrierung abschliessen.
     */
    void bindeChallenge(BenutzerId vorgesehen, String challenge);

    /**
     * Entfernt die Reservierung fuer den Benutzernamen und gibt sie zurueck, sofern sie an
     * {@code challenge} gebunden und um {@code jetzt} noch gueltig ist. Passt die Challenge nicht,
     * bleibt die Reservierung unveraendert (sie gehoert einem anderen Ablauf). Eine abgelaufene
     * Reservierung wird entfernt, aber nicht zurueckgegeben. Hoechstens ein Aufrufer erhaelt sie.
     */
    Optional<AusstehendeRegistrierung> entnimm(Benutzername benutzername, String challenge, Instant jetzt);
}
