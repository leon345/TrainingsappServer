package de.trainingsapp.api.v1.workout;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WorkoutEinladungErstellenV1", description = "Anfrage zum Einladen eines Benutzers zu einem eigenen Workout")
public record WorkoutEinladungErstellenRequest(
        @Schema(description = "Exakter Benutzername des einzuladenden Benutzers", examples = "max.muster", required = true)
        @NotBlank(message = "Benutzername darf nicht leer sein")
        String benutzername
) {
}
