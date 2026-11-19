package de.trainingsapp.application.workout;

import java.util.List;

/**
 * Eingabedaten für eine Übung innerhalb eines Workouts.
 */
public record UebungEingabe(
        String name,
        String variante,
        List<SatzEingabe> saetze
) {
}
