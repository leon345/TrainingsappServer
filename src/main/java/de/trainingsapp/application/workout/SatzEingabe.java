package de.trainingsapp.application.workout;

import java.math.BigDecimal;

/**
 * Eingabedaten für einen einzelnen Satz einer Übung.
 */
public record SatzEingabe(
        Integer wiederholungen,
        Integer dauerSekunden,
        BigDecimal gewichtKg,
        Integer pauseSekunden
) {
}
