package de.trainingsapp.api.v1;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RegistrierungStartenV1")
public record RegistrierungStartenRequest(
        @Schema(required = true, maxLength = 64, description = "Wird dem Authenticator als Kontoname angezeigt")
        String benutzername) {
}
