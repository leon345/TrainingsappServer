package de.trainingsapp.api.v1.fehler;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Einheitliches Fehlerformat für alle /v1-Endpunkte.
 */
@Schema(name = "FehlerV1", description = "Einheitliches Fehlerformat der API")
public record FehlerResponse(
        @Schema(
                description = "Maschinenlesbarer Fehlercode",
                examples = "VALIDIERUNG_FEHLGESCHLAGEN",
                required = true
        )
        String code,
        @Schema(
                description = "Verständliche Fehlerbeschreibung",
                examples = "Die Anfrage enthält ungültige Eingaben.",
                required = true
        )
        String meldung
) {
}
