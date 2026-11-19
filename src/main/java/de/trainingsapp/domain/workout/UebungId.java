package de.trainingsapp.domain.workout;

import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Übungs-ID (UUID).
 */
public record UebungId(UUID wert) {

    public UebungId {
        Objects.requireNonNull(wert, "UebungId darf nicht null sein");
    }

    public static UebungId neu() {
        return new UebungId(UUID.randomUUID());
    }

    public String alsText() {
        return wert.toString();
    }
}
