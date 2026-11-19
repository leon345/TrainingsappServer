package de.trainingsapp.domain;

/**
 * Fachliche Ausnahme für fehlende oder fehlgeschlagene Authentifizierung.
 */
public class DomainNichtAutorisiertException extends DomainException {

    public DomainNichtAutorisiertException(String code, String meldung) {
        super(code, meldung);
    }
}
