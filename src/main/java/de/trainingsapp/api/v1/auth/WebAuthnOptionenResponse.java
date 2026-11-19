package de.trainingsapp.api.v1.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WebAuthnOptionenV1", description = "WebAuthn-Optionen (Creation- oder Request-Options) als JSON-Objekt")
public record WebAuthnOptionenResponse(
        @Schema(description = "WebAuthn-Optionen für navigator.credentials.create/get", required = true)
        JsonNode optionen
) {
}
