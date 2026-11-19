package de.trainingsapp.domain.workout;

import java.util.Objects;
import java.util.UUID;

/**
 * Eindeutige Workout-ID (UUID).
 */
public record WorkoutId(UUID wert) {

    public WorkoutId {
        Objects.requireNonNull(wert, "WorkoutId darf nicht null sein");
    }

    public static WorkoutId neu() {
        return new WorkoutId(UUID.randomUUID());
    }

    public static WorkoutId ausText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("WorkoutId darf nicht leer sein.");
        }
        return new WorkoutId(UUID.fromString(text));
    }

    public String alsText() {
        return wert.toString();
    }
}
