package de.trainingsapp.api.v1.auth;

import de.trainingsapp.domain.benutzer.Benutzer;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BenutzerV1", description = "Informationen zum angemeldeten Benutzerkonto")
public record BenutzerResponse(
        @Schema(description = "Eindeutige Benutzer-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Normalisierter Benutzername", examples = "max.muster", required = true)
        String benutzername,
        @Schema(description = "Rolle des Benutzers", examples = "USER", required = true)
        String rolle,
        @Schema(description = "Erstellungszeitpunkt des Kontos (UTC)", required = true)
        Instant erstelltAm,
        @Schema(description = "Zeitpunkt der DSGVO-Einwilligung (UTC)", required = true)
        Instant einwilligungAm
) {
    public static BenutzerResponse ausDomain(Benutzer benutzer) {
        return new BenutzerResponse(
                benutzer.id().wert(),
                benutzer.benutzername().wert(),
                benutzer.rolle().name(),
                benutzer.erstelltAm(),
                benutzer.einwilligungAm()
        );
    }
}
