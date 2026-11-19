package de.trainingsapp.api.v1.passkey;

import de.trainingsapp.domain.passkey.Passkey;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PasskeyV1", description = "Gespeicherter Passkey eines Benutzers (ohne kryptografisches Schlüsselmaterial)")
public record PasskeyResponse(
        @Schema(description = "Eindeutige Passkey-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Nutzerfreundliche Bezeichnung des Passkeys", examples = "Passkey 1", required = true)
        String bezeichnung,
        @Schema(description = "Erstellungsdatum (UTC)", examples = "2026-11-19", required = true)
        LocalDate erstelltAm,
        @Schema(description = "Datum der letzten Anmeldung (UTC), null falls noch nicht zur Anmeldung genutzt", examples = "2026-11-19")
        LocalDate zuletztVerwendetAm
) {
    public static PasskeyResponse ausDomain(Passkey passkey) {
        return new PasskeyResponse(
                passkey.id().wert(),
                passkey.bezeichnung().wert(),
                passkey.erstelltAm(),
                passkey.zuletztVerwendetAm()
        );
    }
}
