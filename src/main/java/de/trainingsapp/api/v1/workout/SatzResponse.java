package de.trainingsapp.api.v1.workout;

import de.trainingsapp.domain.workout.Satz;
import java.math.BigDecimal;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SatzV1", description = "Gespeicherter Satz einer Übung")
public record SatzResponse(
        @Schema(description = "Eindeutige Satz-ID (UUID)", required = true)
        UUID id,
        @Schema(description = "0-basierte Reihenfolge innerhalb der Übung", minimum = "0", required = true)
        int position,
        @Schema(description = "Anzahl Wiederholungen", minimum = "1", maximum = "10000")
        Integer wiederholungen,
        @Schema(description = "Halte- oder Satzdauer in ganzen Sekunden", minimum = "1", maximum = "86400")
        Integer dauerSekunden,
        @Schema(description = "Zusatz- oder Trainingsgewicht in Kilogramm", minimum = "0", maximum = "9999.99")
        BigDecimal gewichtKg,
        @Schema(description = "Satzpause in ganzen Sekunden", minimum = "0", maximum = "86400")
        Integer pauseSekunden
) {
    public static SatzResponse ausDomain(Satz satz) {
        return new SatzResponse(
                satz.id().wert(),
                satz.position(),
                satz.wiederholungen(),
                satz.dauerSekunden(),
                satz.gewichtKg(),
                satz.pauseSekunden()
        );
    }
}
