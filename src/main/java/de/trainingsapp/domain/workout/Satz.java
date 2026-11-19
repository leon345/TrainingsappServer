package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Einzelner Satz einer Übung (Wiederholungen und/oder Haltedauer in ganzen Sekunden, optional Gewicht und Pause).
 */
public record Satz(
        SatzId id,
        int position,
        Integer wiederholungen,
        Integer dauerSekunden,
        BigDecimal gewichtKg,
        Integer pauseSekunden
) {

    public static final String FEHLERCODE = "UNGUELTIGER_SATZ";
    public static final int MIN_WIEDERHOLUNGEN = 1;
    public static final int MAX_WIEDERHOLUNGEN = 10_000;
    public static final int MIN_DAUER_SEKUNDEN = 1;
    public static final int MAX_DAUER_SEKUNDEN = 86_400;
    public static final int MIN_PAUSE_SEKUNDEN = 0;
    public static final int MAX_PAUSE_SEKUNDEN = 86_400;
    public static final BigDecimal MAX_GEWICHT_KG = new BigDecimal("9999.99");

    public Satz {
        Objects.requireNonNull(id, "SatzId darf nicht null sein");
        if (position < 0) {
            throw new DomainException(FEHLERCODE, "Die Satz-Position darf nicht negativ sein.");
        }
        if (wiederholungen == null && dauerSekunden == null) {
            throw new DomainException(
                    FEHLERCODE,
                    "Ein Satz muss mindestens Wiederholungen oder eine Dauer in Sekunden enthalten."
            );
        }
        if (wiederholungen != null && (wiederholungen < MIN_WIEDERHOLUNGEN || wiederholungen > MAX_WIEDERHOLUNGEN)) {
            throw new DomainException(
                    FEHLERCODE,
                    "Wiederholungen müssen zwischen " + MIN_WIEDERHOLUNGEN + " und " + MAX_WIEDERHOLUNGEN + " liegen."
            );
        }
        if (dauerSekunden != null && (dauerSekunden < MIN_DAUER_SEKUNDEN || dauerSekunden > MAX_DAUER_SEKUNDEN)) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Satzdauer muss zwischen " + MIN_DAUER_SEKUNDEN + " und " + MAX_DAUER_SEKUNDEN + " Sekunden liegen."
            );
        }
        if (gewichtKg != null) {
            if (gewichtKg.compareTo(BigDecimal.ZERO) < 0 || gewichtKg.compareTo(MAX_GEWICHT_KG) > 0) {
                throw new DomainException(
                        FEHLERCODE,
                        "Das Gewicht muss zwischen 0 und 9999.99 kg liegen."
                );
            }
            if (gewichtKg.stripTrailingZeros().scale() > 2) {
                throw new DomainException(
                        FEHLERCODE,
                        "Das Gewicht darf höchstens zwei Nachkommastellen haben."
                );
            }
        }
        if (pauseSekunden != null && (pauseSekunden < MIN_PAUSE_SEKUNDEN || pauseSekunden > MAX_PAUSE_SEKUNDEN)) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Satzpause muss zwischen " + MIN_PAUSE_SEKUNDEN + " und " + MAX_PAUSE_SEKUNDEN + " Sekunden liegen."
            );
        }
    }
}
