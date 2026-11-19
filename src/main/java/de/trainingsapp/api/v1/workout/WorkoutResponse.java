package de.trainingsapp.api.v1.workout;

import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WorkoutV1", description = "Gespeichertes Kraft- oder Calisthenics-Workout inkl. Übungen und Sätzen")
public record WorkoutResponse(
        @Schema(description = "Eindeutige Workout-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Art des Workouts", required = true)
        WorkoutTyp typ,
        @Schema(description = "Titel des Workouts", examples = "Oberkörper Calisthenics", required = true)
        String titel,
        @Schema(description = "Startzeitpunkt des Workouts (UTC)", required = true)
        Instant startZeit,
        @Schema(description = "Gesamtdauer des Workouts in ganzen Sekunden", minimum = "1", maximum = "86400")
        Integer dauerSekunden,
        @Schema(description = "Optionale Trainingsnotiz")
        String notiz,
        @Schema(description = "Sichtbarkeit des Workouts", required = true)
        Sichtbarkeit sichtbarkeit,
        @Schema(description = "Herkunft des Workouts", required = true)
        Herkunft herkunft,
        @Schema(description = "Übungen des Workouts in Ausführungsreihenfolge", required = true)
        List<UebungResponse> uebungen,
        @Schema(description = "Erstellungszeitpunkt (UTC)", required = true)
        Instant erstelltAm,
        @Schema(description = "Zeitpunkt der letzten Änderung (UTC)", required = true)
        Instant geaendertAm,
        @Schema(description = "Version für optimistische Sperre", minimum = "0", required = true)
        long version
) {
    public static WorkoutResponse ausDomain(Workout workout) {
        return new WorkoutResponse(
                workout.id().wert(),
                workout.typ(),
                workout.titel(),
                workout.startZeit(),
                workout.dauerSekunden(),
                workout.notiz(),
                workout.sichtbarkeit(),
                workout.herkunft(),
                workout.uebungen().stream().map(UebungResponse::ausDomain).toList(),
                workout.erstelltAm(),
                workout.geaendertAm(),
                workout.version()
        );
    }
}
