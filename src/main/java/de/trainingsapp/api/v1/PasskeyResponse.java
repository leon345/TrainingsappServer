package de.trainingsapp.api.v1;

import de.trainingsapp.domain.passkey.Passkey;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PasskeyV1")
public record PasskeyResponse(
        @Schema(description = "Credential-ID (Base64URL)") String id,
        Instant erstelltAm) {

    static PasskeyResponse from(Passkey p) {
        return new PasskeyResponse(p.id().wert(), p.erstelltAm());
    }
}
