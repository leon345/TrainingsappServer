package de.trainingsapp.api.v1.auth;

import de.trainingsapp.application.passkey.PasskeyZeremonie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PasskeyRegistrierungsCredentialV1", description = "WebAuthn-Antwort von navigator.credentials.create()")
public record PasskeyRegistrierungsCredentialRequest(
        @NotBlank(message = "id darf nicht leer sein")
        @Schema(description = "Base64URL-kodierte Credential-ID", required = true)
        String id,
        @NotBlank(message = "rawId darf nicht leer sein")
        @Schema(description = "Base64URL-kodierte Raw-ID", required = true)
        String rawId,
        @NotBlank(message = "type darf nicht leer sein")
        @Schema(description = "Credential-Typ (public-key)", examples = "public-key", required = true)
        String type,
        @NotNull(message = "response darf nicht fehlen")
        @Valid
        @Schema(description = "Attestierungsantwort des Authenticators", required = true)
        WebAuthnRegistrierungsAntwortRequest response
) {
    @Schema(name = "WebAuthnRegistrierungsAntwortV1", description = "AuthenticatorAttestationResponse")
    public record WebAuthnRegistrierungsAntwortRequest(
            @NotBlank(message = "clientDataJSON darf nicht leer sein")
            @Schema(description = "Base64URL-kodiertes clientDataJSON", required = true)
            String clientDataJSON,
            @NotBlank(message = "attestationObject darf nicht leer sein")
            @Schema(description = "Base64URL-kodiertes attestationObject", required = true)
            String attestationObject
    ) {
    }

    public PasskeyZeremonie.RegistrierungsAntwort zuApplication() {
        return new PasskeyZeremonie.RegistrierungsAntwort(
                id,
                rawId,
                type,
                response.clientDataJSON(),
                response.attestationObject()
        );
    }
}
