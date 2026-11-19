package de.trainingsapp.api.v1.workout;

import de.trainingsapp.domain.workout.Sichtbarkeit;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WorkoutSichtbarkeitV1", description = "Anfrage zum Umschalten der Sichtbarkeit eines eigenen Workouts")
public record WorkoutSichtbarkeitRequest(
        @Schema(description = "Neue Sichtbarkeit des Workouts", required = true)
        @NotNull(message = "Sichtbarkeit darf nicht fehlen")
        Sichtbarkeit sichtbarkeit
) {
}
