package de.trainingsapp.domain;

import java.util.Objects;

/**
 * Basis-Ausnahme für fachliche Fehler in der Domain-Schicht.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String meldung) {
        super(Objects.requireNonNull(meldung, "meldung darf nicht null sein"));
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code darf nicht leer sein");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
