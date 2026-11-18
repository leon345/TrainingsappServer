package de.trainingsapp.api.v1;

import de.trainingsapp.domain.passkey.Passkey;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "PasskeyV1")
public record PasskeyResponse(
        @Schema(description = "Credential-ID (Base64URL)") String id,
        @Schema(description = "Sichtbare Bezeichnung, automatisch Passkey n", maxLength = 50) String bezeichnung,
        @Schema(description = "Tag der Registrierung (UTC)") LocalDate erstelltAm,
        @Schema(description = "Tag der letzten Anmeldung (UTC), fehlt wenn nie verwendet", nullable = true)
        LocalDate zuletztVerwendetAm) {

    static PasskeyResponse from(Passkey p) {
        return new PasskeyResponse(p.id().wert(), p.bezeichnung().wert(), p.erstelltAm(), p.zuletztVerwendetAm());
    }
}
