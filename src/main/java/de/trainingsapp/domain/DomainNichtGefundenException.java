package de.trainingsapp.domain;

/**
 * Fachliche Ausnahme für nicht gefundene Ressourcen.
 */
public class DomainNichtGefundenException extends DomainException {

    public DomainNichtGefundenException(String code, String meldung) {
        super(code, meldung);
    }
}
