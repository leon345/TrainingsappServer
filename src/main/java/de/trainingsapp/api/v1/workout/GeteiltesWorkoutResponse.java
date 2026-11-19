package de.trainingsapp.api.v1.workout;

import de.trainingsapp.application.workout.GeteiltesWorkoutAnsicht;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "GeteiltesWorkoutV1", description = "Workout, zu dem der angemeldete Benutzer explizit eingeladen wurde")
public record GeteiltesWorkoutResponse(
        @Schema(description = "Eindeutige Workout-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Benutzer-ID des Workout-Besitzers (UUID)", required = true)
        UUID ownerId,
        @Schema(description = "Normalisierter Benutzername des Workout-Besitzers", examples = "max.muster", required = true)
        String ownerBenutzername,
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
        @Schema(description = "Erstellungszeitpunkt des Workouts (UTC)", required = true)
        Instant erstelltAm,
        @Schema(description = "Zeitpunkt der letzten Änderung (UTC)", required = true)
        Instant geaendertAm,
        @Schema(description = "Version für optimistische Sperre", minimum = "0", required = true)
        long version,
        @Schema(description = "Zeitpunkt der Einladung (UTC)", required = true)
        Instant eingeladenAm
) {
    public static GeteiltesWorkoutResponse ausAnsicht(GeteiltesWorkoutAnsicht ansicht) {
        Workout workout = ansicht.workout();
        return new GeteiltesWorkoutResponse(
                workout.id().wert(),
                workout.ownerId().wert(),
                ansicht.ownerBenutzername().wert(),
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
                workout.version(),
                ansicht.eingeladenAm()
        );
    }
}
