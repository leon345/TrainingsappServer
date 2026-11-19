package de.trainingsapp.domain;

/**
 * Fachliche Ausnahme für Zustandskonflikte (z. B. bereits vergebener Benutzername oder letzter Passkey).
 */
public class DomainKonfliktException extends DomainException {

    public DomainKonfliktException(String code, String meldung) {
        super(code, meldung);
    }
}
