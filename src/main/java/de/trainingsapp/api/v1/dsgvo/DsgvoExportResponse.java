package de.trainingsapp.api.v1.dsgvo;

import de.trainingsapp.api.v1.passkey.PasskeyResponse;
import de.trainingsapp.api.v1.timer.TimerEinstellungResponse;
import de.trainingsapp.api.v1.workout.UebungResponse;
import de.trainingsapp.api.v1.workout.WorkoutEinladungResponse;
import de.trainingsapp.application.dsgvo.DsgvoExportDaten;
import de.trainingsapp.application.timer.TimerEinstellungAnsicht;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutEinladungStatus;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "DsgvoExportV1", description = "Vollständiger DSGVO-Datenexport aller gespeicherten Daten des angemeldeten Benutzers")
public record DsgvoExportResponse(
        @Schema(description = "Zeitpunkt des Exports (UTC)", required = true)
        Instant exportiertAm,
        @Schema(description = "Profildaten des Benutzers", required = true)
        BenutzerExport benutzer,
        @Schema(description = "Passkey-Metadaten (ohne kryptografisches Schlüsselmaterial)", required = true)
        List<PasskeyResponse> passkeys,
        @Schema(description = "Gespeicherte Intervalltimer-Einstellung (null, falls noch keine gespeichert)")
        TimerEinstellungResponse timerEinstellung,
        @Schema(description = "Eigene Workouts inkl. Übungen, Sätzen und ausgehenden Einladungen", required = true)
        List<WorkoutExport> workouts,
        @Schema(description = "Erhaltene Einladungen zu Workouts anderer Benutzer", required = true)
        List<ErhalteneEinladungExport> erhalteneEinladungen
) {

    @Schema(name = "DsgvoBenutzerV1", description = "Exportierte Benutzerprofildaten")
    public record BenutzerExport(
            @Schema(description = "Eindeutige Benutzer-ID (UUID)", required = true)
            UUID id,
            @Schema(description = "Normalisierter Benutzername", examples = "max.muster", required = true)
            String benutzername,
            @Schema(description = "Rolle des Benutzers", required = true)
            Rolle rolle,
            @Schema(description = "Erstellungszeitpunkt des Kontos (UTC)", required = true)
            Instant erstelltAm,
            @Schema(description = "Zeitpunkt der Einwilligung (UTC)", required = true)
            Instant einwilligungAm
    ) {
        public static BenutzerExport ausDomain(Benutzer benutzer) {
            return new BenutzerExport(
                    benutzer.id().wert(),
                    benutzer.benutzername().wert(),
                    benutzer.rolle(),
                    benutzer.erstelltAm(),
                    benutzer.einwilligungAm()
            );
        }
    }

    @Schema(name = "DsgvoWorkoutV1", description = "Exportiertes eigenes Workout inkl. Übungen, Sätzen und ausgehenden Einladungen")
    public record WorkoutExport(
            @Schema(description = "Eindeutige Workout-ID (UUID)", required = true)
            UUID id,
            @Schema(description = "Art des Workouts", required = true)
            WorkoutTyp typ,
            @Schema(description = "Titel des Workouts", required = true)
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
            @Schema(description = "Externe Quell-ID bei importierten Workouts")
            String externeId,
            @Schema(description = "Übungen des Workouts in Ausführungsreihenfolge", required = true)
            List<UebungResponse> uebungen,
            @Schema(description = "Ausgehende Einladungen für dieses Workout", required = true)
            List<WorkoutEinladungResponse> einladungen,
            @Schema(description = "Erstellungszeitpunkt (UTC)", required = true)
            Instant erstelltAm,
            @Schema(description = "Zeitpunkt der letzten Änderung (UTC)", required = true)
            Instant geaendertAm,
            @Schema(description = "Version", minimum = "0", required = true)
            long version
    ) {
        public static WorkoutExport ausExport(DsgvoExportDaten.WorkoutExport export) {
            Workout workout = export.workout();
            return new WorkoutExport(
                    workout.id().wert(),
                    workout.typ(),
                    workout.titel(),
                    workout.startZeit(),
                    workout.dauerSekunden(),
                    workout.notiz(),
                    workout.sichtbarkeit(),
                    workout.herkunft(),
                    workout.externeId(),
                    workout.uebungen().stream().map(UebungResponse::ausDomain).toList(),
                    export.einladungen().stream().map(WorkoutEinladungResponse::ausAnsicht).toList(),
                    workout.erstelltAm(),
                    workout.geaendertAm(),
                    workout.version()
            );
        }
    }

    @Schema(name = "DsgvoErhalteneEinladungV1", description = "Exportierte erhaltene Einladung zu einem Workout")
    public record ErhalteneEinladungExport(
            @Schema(description = "Eindeutige Einladungs-ID (UUID)", required = true)
            UUID id,
            @Schema(description = "Workout-ID (UUID)", required = true)
            UUID workoutId,
            @Schema(description = "Titel des geteilten Workouts", required = true)
            String workoutTitel,
            @Schema(description = "Benutzer-ID des Workout-Besitzers (UUID)", required = true)
            UUID ownerId,
            @Schema(description = "Normalisierter Benutzername des Workout-Besitzers", required = true)
            String ownerBenutzername,
            @Schema(description = "Status der Einladung", required = true)
            WorkoutEinladungStatus status,
            @Schema(description = "Erstellungszeitpunkt der Einladung (UTC)", required = true)
            Instant erstelltAm
    ) {
        public static ErhalteneEinladungExport ausExport(DsgvoExportDaten.ErhalteneEinladungExport export) {
            return new ErhalteneEinladungExport(
                    export.einladung().id().wert(),
                    export.einladung().workoutId().wert(),
                    export.workoutTitel(),
                    export.ownerId().wert(),
                    export.ownerBenutzername().wert(),
                    export.einladung().status(),
                    export.einladung().erstelltAm()
            );
        }
    }

    public static DsgvoExportResponse ausDaten(DsgvoExportDaten daten) {
        TimerEinstellungResponse timer = daten.timerEinstellung() == null
                ? null
                : TimerEinstellungResponse.ausAnsicht(new TimerEinstellungAnsicht(daten.timerEinstellung(), true));
        return new DsgvoExportResponse(
                daten.exportiertAm(),
                BenutzerExport.ausDomain(daten.benutzer()),
                daten.passkeys().stream().map(PasskeyResponse::ausDomain).toList(),
                timer,
                daten.workouts().stream().map(WorkoutExport::ausExport).toList(),
                daten.erhalteneEinladungen().stream().map(ErhalteneEinladungExport::ausExport).toList()
        );
    }
}
