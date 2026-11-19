package de.trainingsapp.api.v1.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AnmeldenOptionenAnfrageV1", description = "Optionale Anfrage für Passkey-Anmeldeoptionen (leer für Discoverable Login)")
public record AnmeldenOptionenRequest(
        @Schema(description = "Optionaler Benutzername; leer für Discoverable Passkey Login", examples = "max.muster")
        String benutzername
) {
}
