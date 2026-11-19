package de.trainingsapp.api.v1.workout;

import de.trainingsapp.application.workout.WorkoutEinladungAnsicht;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungStatus;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WorkoutEinladungV1", description = "Einladung eines Benutzers zu einem Workout")
public record WorkoutEinladungResponse(
        @Schema(description = "Eindeutige Einladungs-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Workout-ID (UUID)", required = true)
        UUID workoutId,
        @Schema(description = "Benutzer-ID des eingeladenen Benutzers (UUID)", required = true)
        UUID eingeladenerId,
        @Schema(description = "Normalisierter Benutzername des eingeladenen Benutzers", examples = "max.muster", required = true)
        String eingeladenerBenutzername,
        @Schema(description = "Status der Einladung", required = true)
        WorkoutEinladungStatus status,
        @Schema(description = "Erstellungszeitpunkt der Einladung (UTC)", required = true)
        Instant erstelltAm
) {
    public static WorkoutEinladungResponse ausAnsicht(WorkoutEinladungAnsicht ansicht) {
        WorkoutEinladung einladung = ansicht.einladung();
        return new WorkoutEinladungResponse(
                einladung.id().wert(),
                einladung.workoutId().wert(),
                einladung.eingeladenerId().wert(),
                ansicht.eingeladenerBenutzername().wert(),
                einladung.status(),
                einladung.erstelltAm()
        );
    }
}
