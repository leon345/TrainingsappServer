package de.trainingsapp.api.v1;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RegistrierungStartenV1")
public record RegistrierungStartenRequest(
        @Schema(required = true, minLength = 1, maxLength = 64,
                description = "Pseudonym, keine E-Mail-Adresse und kein Klarname (ohne @). "
                        + "Wird dem Authenticator als Kontoname angezeigt.")
        String benutzername) {
}
