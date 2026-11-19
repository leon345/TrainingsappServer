package de.trainingsapp.api.v1.passkey;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PasskeyUmbenennenAnfrageV1", description = "Anfrage zum Umbenennen eines Passkeys")
public record PasskeyUmbenennenRequest(
        @NotBlank(message = "bezeichnung darf nicht leer sein")
        @Schema(description = "Neue Bezeichnung des Passkeys (1–50 Zeichen)", examples = "YubiKey Arbeit", required = true)
        String bezeichnung
) {
}
