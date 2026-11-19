package de.trainingsapp.api.v1.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "BenutzerKurzV1", description = "Öffentliche Minimaldaten eines Benutzers zur Vorbereitung von Einladungen")
public record BenutzerKurzResponse(
        @Schema(description = "Eindeutige Benutzer-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "Normalisierter Benutzername", examples = "max.muster", required = true)
        String benutzername
) {
    public static BenutzerKurzResponse ausDomain(Benutzer benutzer) {
        return new BenutzerKurzResponse(benutzer.id().wert(), benutzer.benutzername().wert());
    }
}
