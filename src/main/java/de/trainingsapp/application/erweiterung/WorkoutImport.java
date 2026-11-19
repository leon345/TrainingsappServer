package de.trainingsapp.application.erweiterung;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Workout;

/**
 * Interner Erweiterungsport für zukünftige Workout-Importe (z. B. Apple Health / HealthKit).
 * Kein öffentlicher REST-Endpunkt (Nutzerentscheid B / E22).
 */
public interface WorkoutImport {

    /**
     * Importiert ein Workout idempotent über das Tupel (ownerId, herkunft, externeId).
     * Ein erneuter Import derselben Kombination aktualisiert das vorhandene Workout statt ein Duplikat anzulegen.
     * Importierte Workouts erhalten stets die Sichtbarkeit PRIVAT.
     */
    Workout importiere(BenutzerId ownerId, Herkunft herkunft, String externeId, WorkoutImportDaten daten);
}
