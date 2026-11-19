package de.trainingsapp.application.dsgvo;

import de.trainingsapp.application.workout.WorkoutEinladungAnsicht;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import java.time.Instant;
import java.util.List;

/**
 * Vollständiger DSGVO-Datenexport eines Benutzers (ohne kryptografisches Schlüsselmaterial).
 */
public record DsgvoExportDaten(
        Instant exportiertAm,
        Benutzer benutzer,
        List<Passkey> passkeys,
        IntervallEinstellung timerEinstellung,
        List<WorkoutExport> workouts,
        List<ErhalteneEinladungExport> erhalteneEinladungen
) {

    public record WorkoutExport(
            Workout workout,
            List<WorkoutEinladungAnsicht> einladungen
    ) {
    }

    public record ErhalteneEinladungExport(
            WorkoutEinladung einladung,
            String workoutTitel,
            de.trainingsapp.domain.benutzer.BenutzerId ownerId,
            Benutzername ownerBenutzername
    ) {
    }
}
