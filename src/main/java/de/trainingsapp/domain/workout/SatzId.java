package de.trainingsapp.domain.workout;

import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Satz-ID (UUID).
 */
public record SatzId(UUID wert) {

    public SatzId {
        Objects.requireNonNull(wert, "SatzId darf nicht null sein");
    }

    public static SatzId neu() {
        return new SatzId(UUID.randomUUID());
    }

    public String alsText() {
        return wert.toString();
    }
}
