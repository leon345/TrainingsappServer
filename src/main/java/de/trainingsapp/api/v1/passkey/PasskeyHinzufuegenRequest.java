package de.trainingsapp.api.v1.passkey;

import de.trainingsapp.api.v1.auth.PasskeyRegistrierungsCredentialRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PasskeyHinzufuegenAnfrageV1", description = "Anfrage zum Hinzufügen eines weiteren Passkeys")
public record PasskeyHinzufuegenRequest(
        @NotNull(message = "credential darf nicht fehlen")
        @Valid
        @Schema(description = "WebAuthn-Registrierungsantwort", required = true)
        PasskeyRegistrierungsCredentialRequest credential,
        @Schema(description = "Optionale Bezeichnung (sonst automatisch 'Passkey n')", examples = "Smartphone")
        String bezeichnung
) {
}
