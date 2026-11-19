package de.trainingsapp.api.v1.timer;

import de.trainingsapp.application.timer.TimerEinstellungAnsicht;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TimerEinstellungV1", description = "Intervalltimer-Einstellung eines Benutzers in ganzen Sekunden")
public record TimerEinstellungResponse(
        @Schema(
                description = "Trainingsdauer in ganzen Sekunden",
                minimum = "1",
                maximum = "86400",
                examples = "30",
                required = true
        )
        int trainingSekunden,
        @Schema(
                description = "Pausendauer in ganzen Sekunden",
                minimum = "0",
                maximum = "86400",
                examples = "10",
                required = true
        )
        int pauseSekunden,
        @Schema(
                description = "Gibt an, ob bereits eine benutzerspezifische Einstellung gespeichert wurde",
                readOnly = true,
                required = true
        )
        boolean gespeichert
) {
    public static TimerEinstellungResponse ausAnsicht(TimerEinstellungAnsicht ansicht) {
        return new TimerEinstellungResponse(
                ansicht.einstellung().trainingSekunden(),
                ansicht.einstellung().pauseSekunden(),
                ansicht.gespeichert()
        );
    }
}
