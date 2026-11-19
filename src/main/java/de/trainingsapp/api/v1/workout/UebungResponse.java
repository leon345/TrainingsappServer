package de.trainingsapp.api.v1.workout;

import de.trainingsapp.domain.workout.Uebung;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UebungV1", description = "Gespeicherte Übung eines Workouts inkl. Sätzen")
public record UebungResponse(
        @Schema(description = "Eindeutige Übungs-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "0-basierte Reihenfolge innerhalb des Workouts", minimum = "0", required = true)
        int position,
        @Schema(description = "Name der Übung", examples = "Front Lever", required = true)
        String name,
        @Schema(description = "Optionale Progressionsstufe oder Variante", examples = "Tuck")
        String variante,
        @Schema(description = "Sätze der Übung in Ausführungsreihenfolge", required = true)
        List<SatzResponse> saetze
) {
    public static UebungResponse ausDomain(Uebung uebung) {
        return new UebungResponse(
                uebung.id().wert(),
                uebung.position(),
                uebung.name(),
                uebung.variante(),
                uebung.saetze().stream().map(SatzResponse::ausDomain).toList()
        );
    }
}
