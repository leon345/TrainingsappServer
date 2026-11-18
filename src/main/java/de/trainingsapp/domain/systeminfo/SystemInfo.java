package de.trainingsapp.domain.systeminfo;

import java.util.Objects;

/**
 * Value Object mit Basisinformationen zur laufenden Anwendung.
 */
public record SystemInfo(String name, String version) {

    public SystemInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
    }
}
