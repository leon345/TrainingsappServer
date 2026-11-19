package de.trainingsapp.api.v1.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RegistrierenOptionenAnfrageV1", description = "Anfrage zum Starten einer offenen Passkey-Registrierung")
public record RegistrierenOptionenRequest(
        @NotBlank(message = "benutzername darf nicht leer sein")
        @Schema(description = "Gewünschter Benutzername (3–32 Zeichen, ASCII)", examples = "max.muster", required = true)
        String benutzername,
        @NotNull(message = "einwilligungErteilt muss angegeben werden")
        @Schema(description = "Ausdrückliche Einwilligung in die Datenverarbeitung", examples = "true", required = true)
        Boolean einwilligungErteilt
) {
}
