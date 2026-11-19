package de.trainingsapp.domain.workout;

import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Einladungs-ID (UUID).
 */
public record WorkoutEinladungId(UUID wert) {

    public WorkoutEinladungId {
        Objects.requireNonNull(wert, "WorkoutEinladungId darf nicht null sein");
    }

    public static WorkoutEinladungId neu() {
        return new WorkoutEinladungId(UUID.randomUUID());
    }

    public String alsText() {
        return wert.toString();
    }
}
