package de.trainingsapp.api.v1.workout;

import de.trainingsapp.application.workout.UebungEingabe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "UebungEingabeV1",
        description = "Eingabedaten für eine Übung innerhalb eines Workouts inkl. optionaler Progressionsvariante"
)
public record UebungRequest(
        @NotBlank(message = "Übungsname darf nicht leer sein")
        @Size(max = 100, message = "Übungsname darf höchstens 100 Zeichen lang sein")
        @Schema(description = "Name der Übung", maxLength = 100, examples = "Front Lever", required = true)
        String name,
        @Size(max = 100, message = "Übungsvariante darf höchstens 100 Zeichen lang sein")
        @Schema(description = "Optionale Progressionsstufe oder Variante", maxLength = 100, examples = "Tuck")
        String variante,
        @Schema(description = "Sätze der Übung in Ausführungsreihenfolge")
        List<@NotNull(message = "Satz darf nicht null sein") @Valid SatzRequest> saetze
) {
    public UebungEingabe zuApplication() {
        return new UebungEingabe(
                name,
                variante,
                saetze == null ? List.of() : saetze.stream().map(SatzRequest::zuApplication).toList()
        );
    }
}
