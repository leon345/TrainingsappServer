package de.trainingsapp.api.v1.workout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.trainingsapp.application.workout.SatzEingabe;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "SatzEingabeV1",
        description = "Eingabedaten für einen einzelnen Satz (Wiederholungen und/oder Haltedauer in ganzen Sekunden)"
)
public record SatzRequest(
        @Min(value = 1, message = "wiederholungen muss mindestens 1 sein")
        @Max(value = 10000, message = "wiederholungen darf höchstens 10000 sein")
        @Schema(description = "Anzahl Wiederholungen", minimum = "1", maximum = "10000", examples = "10")
        Integer wiederholungen,
        @Min(value = 1, message = "dauerSekunden muss mindestens 1 Sekunde betragen")
        @Max(value = 86400, message = "dauerSekunden darf höchstens 86400 Sekunden betragen")
        @Schema(description = "Halte- oder Satzdauer in ganzen Sekunden", minimum = "1", maximum = "86400", examples = "30")
        Integer dauerSekunden,
        @DecimalMin(value = "0.00", message = "gewichtKg darf nicht negativ sein")
        @DecimalMax(value = "9999.99", message = "gewichtKg darf höchstens 9999.99 kg betragen")
        @Digits(integer = 4, fraction = 2, message = "gewichtKg darf höchstens 4 Vorkomma- und 2 Nachkommastellen haben")
        @Schema(description = "Zusatz- oder Trainingsgewicht in Kilogramm", minimum = "0", maximum = "9999.99", examples = "60.5")
        BigDecimal gewichtKg,
        @Min(value = 0, message = "pauseSekunden darf nicht negativ sein")
        @Max(value = 86400, message = "pauseSekunden darf höchstens 86400 Sekunden betragen")
        @Schema(description = "Satzpause in ganzen Sekunden", minimum = "0", maximum = "86400", examples = "90")
        Integer pauseSekunden
) {
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Ein Satz muss mindestens wiederholungen oder dauerSekunden enthalten")
    public boolean isLeistungAngegeben() {
        return wiederholungen != null || dauerSekunden != null;
    }

    public SatzEingabe zuApplication() {
        return new SatzEingabe(wiederholungen, dauerSekunden, gewichtKg, pauseSekunden);
    }
}
