package de.trainingsapp.api.v1.workout;

import de.trainingsapp.application.workout.WorkoutEingabe;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.WorkoutTyp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "WorkoutAktualisierenV1",
        description = "Anfrage zum vollständigen Aktualisieren eines Workouts inkl. optimistischer Sperrversion"
)
public record WorkoutAktualisierenRequest(
        @NotNull(message = "version darf nicht fehlen")
        @Min(value = 0, message = "version darf nicht negativ sein")
        @Schema(description = "Erwartete Version für optimistische Sperre", minimum = "0", examples = "0", required = true)
        Long version,
        @NotNull(message = "Workout-Typ darf nicht fehlen")
        @Schema(description = "Art des Workouts", examples = "KRAFT", required = true)
        WorkoutTyp typ,
        @NotBlank(message = "Workout-Titel darf nicht leer sein")
        @Size(max = 100, message = "Workout-Titel darf höchstens 100 Zeichen lang sein")
        @Schema(description = "Titel des Workouts", maxLength = 100, examples = "Unterkörper Kraft", required = true)
        String titel,
        @NotNull(message = "startZeit darf nicht fehlen")
        @Schema(description = "Startzeitpunkt des Workouts (UTC)", examples = "2026-11-19T18:00:00Z", required = true)
        Instant startZeit,
        @Min(value = 1, message = "dauerSekunden muss mindestens 1 Sekunde betragen")
        @Max(value = 86400, message = "dauerSekunden darf höchstens 86400 Sekunden betragen")
        @Schema(description = "Gesamtdauer des Workouts in ganzen Sekunden", minimum = "1", maximum = "86400", examples = "3600")
        Integer dauerSekunden,
        @Size(max = 500, message = "notiz darf höchstens 500 Zeichen lang sein")
        @Schema(description = "Optionale Trainingsnotiz", maxLength = 500)
        String notiz,
        @Schema(description = "Sichtbarkeit des Workouts (falls null bleibt die bisherige Sichtbarkeit erhalten)")
        Sichtbarkeit sichtbarkeit,
        @Schema(description = "Übungen des Workouts in Ausführungsreihenfolge")
        List<@NotNull(message = "Übung darf nicht null sein") @Valid UebungRequest> uebungen
) {
    public WorkoutEingabe zuApplication() {
        return new WorkoutEingabe(
                typ,
                titel,
                startZeit,
                dauerSekunden,
                notiz,
                sichtbarkeit,
                uebungen == null ? List.of() : uebungen.stream().map(UebungRequest::zuApplication).toList()
        );
    }
}
