package de.trainingsapp.api.v1.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RegistrierenAnfrageV1", description = "Abschluss der Passkey-Registrierung")
public record RegistrierenRequest(
        @NotBlank(message = "benutzername darf nicht leer sein")
        @Schema(description = "Benutzername aus dem Options-Schritt", examples = "max.muster", required = true)
        String benutzername,
        @NotNull(message = "credential darf nicht fehlen")
        @Valid
        @Schema(description = "WebAuthn-Registrierungsantwort", required = true)
        PasskeyRegistrierungsCredentialRequest credential,
        @Schema(description = "Optionale Bezeichnung für den ersten Passkey (sonst automatisch 'Passkey 1')", examples = "Laptop")
        String bezeichnung
) {
}
