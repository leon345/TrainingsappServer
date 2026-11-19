package de.trainingsapp.api.v1.auth;

import de.trainingsapp.application.passkey.PasskeyZeremonie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AnmeldenAnfrageV1", description = "Abschluss der Passkey-Anmeldung")
public record AnmeldenRequest(
        @NotNull(message = "credential darf nicht fehlen")
        @Valid
        @Schema(description = "WebAuthn-Antwort von navigator.credentials.get()", required = true)
        PasskeyAnmeldeCredentialRequest credential
) {
    @Schema(name = "PasskeyAnmeldeCredentialV1", description = "WebAuthn-Assertion-Credential")
    public record PasskeyAnmeldeCredentialRequest(
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
            @Schema(description = "Assertion-Antwort des Authenticators", required = true)
            WebAuthnAnmeldeAntwortRequest response
    ) {
        public PasskeyZeremonie.AnmeldeAntwort zuApplication() {
            return new PasskeyZeremonie.AnmeldeAntwort(
                    id,
                    rawId,
                    type,
                    response.clientDataJSON(),
                    response.authenticatorData(),
                    response.signature(),
                    response.userHandle()
            );
        }
    }

    @Schema(name = "WebAuthnAnmeldeAntwortV1", description = "AuthenticatorAssertionResponse")
    public record WebAuthnAnmeldeAntwortRequest(
            @NotBlank(message = "clientDataJSON darf nicht leer sein")
            @Schema(description = "Base64URL-kodiertes clientDataJSON", required = true)
            String clientDataJSON,
            @NotBlank(message = "authenticatorData darf nicht leer sein")
            @Schema(description = "Base64URL-kodiertes authenticatorData", required = true)
            String authenticatorData,
            @NotBlank(message = "signature darf nicht leer sein")
            @Schema(description = "Base64URL-kodierte Signatur", required = true)
            String signature,
            @Schema(description = "Optionales Base64URL-kodiertes User-Handle")
            String userHandle
    ) {
    }
}
