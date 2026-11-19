package de.trainingsapp.domain.system;

/**
 * Fachliches Wertobjekt für Systeminformationen der Anwendung.
 */
public record SystemInfo(String name, String version) {

    public SystemInfo {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name darf nicht leer sein");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version darf nicht leer sein");
        }
    }
}
