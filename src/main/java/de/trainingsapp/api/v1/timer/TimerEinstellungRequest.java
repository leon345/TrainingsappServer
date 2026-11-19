package de.trainingsapp.api.v1.timer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
        name = "TimerEinstellungAktualisierenV1",
        description = "Anfrage zum Speichern der benutzerspezifischen Intervalltimer-Einstellung in ganzen Sekunden"
)
public record TimerEinstellungRequest(
        @NotNull(message = "trainingSekunden darf nicht fehlen")
        @Min(value = 1, message = "trainingSekunden muss mindestens 1 Sekunde betragen")
        @Max(value = 86400, message = "trainingSekunden darf höchstens 86400 Sekunden betragen")
        @Schema(
                description = "Trainingsdauer in ganzen Sekunden",
                minimum = "1",
                maximum = "86400",
                examples = "45",
                required = true
        )
        Integer trainingSekunden,
        @NotNull(message = "pauseSekunden darf nicht fehlen")
        @Min(value = 0, message = "pauseSekunden darf nicht negativ sein")
        @Max(value = 86400, message = "pauseSekunden darf höchstens 86400 Sekunden betragen")
        @Schema(
                description = "Pausendauer in ganzen Sekunden",
                minimum = "0",
                maximum = "86400",
                examples = "15",
                required = true
        )
        Integer pauseSekunden
) {
}
