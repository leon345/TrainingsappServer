package de.trainingsapp.application.erweiterung;

import de.trainingsapp.application.workout.WorkoutService;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Uebung;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementierung des internen WorkoutImport-Ports (Nutzerentscheid B / E22).
 * Erzwingt identische Domain-Validierung, Sichtbarkeit PRIVAT und idempotentes Upsert über (ownerId, herkunft, externeId).
 */
@ApplicationScoped
public class WorkoutImportService implements WorkoutImport {

    private final WorkoutRepository workoutRepository;
    private final Uhr uhr;

    public WorkoutImportService(WorkoutRepository workoutRepository, Uhr uhr) {
        this.workoutRepository = workoutRepository;
        this.uhr = uhr;
    }

    @Override
    @Transactional
    public Workout importiere(BenutzerId ownerId, Herkunft herkunft, String externeId, WorkoutImportDaten daten) {
        Objects.requireNonNull(ownerId, "ownerId darf nicht null sein");
        if (daten == null) {
            throw new DomainException(Workout.FEHLERCODE, "Importdaten dürfen nicht fehlen.");
        }
        if (herkunft == null || herkunft == Herkunft.MANUELL) {
            throw new DomainException(
                    "UNGUELTIGE_HERKUNFT",
                    "Für den Workout-Import muss eine externe Herkunft (nicht MANUELL) angegeben werden."
            );
        }
        if (externeId == null || externeId.isBlank()) {
            throw new DomainException(
                    "UNGUELTIGE_EXTERNE_ID",
                    "Für den Workout-Import ist eine nicht-leere externe ID erforderlich."
            );
        }
        String bereinigteExterneId = externeId.trim();
        if (bereinigteExterneId.length() > Workout.MAX_EXTERNE_ID_LAENGE) {
            throw new DomainException(
                    "UNGUELTIGE_EXTERNE_ID",
                    "Die externe ID darf höchstens " + Workout.MAX_EXTERNE_ID_LAENGE + " Zeichen lang sein."
            );
        }

        List<Uebung> uebungen = WorkoutService.baueUebungen(daten.uebungen());
        Instant jetzt = uhr.jetzt();

        Optional<Workout> vorhanden = workoutRepository.findeNachOwnerUndHerkunftUndExterneId(
                ownerId,
                herkunft,
                bereinigteExterneId
        );
        if (vorhanden.isPresent()) {
            Workout bestehend = vorhanden.get();
            Workout aktualisiert = bestehend.alsImportAktualisieren(
                    daten.typ(),
                    daten.titel(),
                    daten.startZeit(),
                    daten.dauerSekunden(),
                    daten.notiz(),
                    uebungen,
                    jetzt
            );
            return workoutRepository.aktualisieren(aktualisiert, bestehend.version());
        }

        Workout neu = new Workout(
                WorkoutId.neu(),
                ownerId,
                daten.typ(),
                daten.titel(),
                daten.startZeit(),
                daten.dauerSekunden(),
                daten.notiz(),
                Sichtbarkeit.PRIVAT,
                herkunft,
                bereinigteExterneId,
                uebungen,
                jetzt,
                jetzt,
                0L
        );
        workoutRepository.speichern(neu);
        return neu;
    }
}
