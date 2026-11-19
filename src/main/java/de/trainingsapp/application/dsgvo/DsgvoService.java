package de.trainingsapp.application.dsgvo;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.workout.WorkoutEinladungAnsicht;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerNichtGefundenException;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.IntervallEinstellungRepository;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungRepository;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Anwendungsservice für DSGVO-Datenexport und vollständige Kontolöschung.
 * Protokolliert bewusst keine personenbezogenen Daten oder Trainingsinhalte.
 */
@ApplicationScoped
public class DsgvoService {

    private final BenutzerRepository benutzerRepository;
    private final PasskeyRepository passkeyRepository;
    private final IntervallEinstellungRepository intervallEinstellungRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutEinladungRepository workoutEinladungRepository;
    private final AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository;
    private final Anmeldesitzung anmeldesitzung;
    private final Uhr uhr;

    public DsgvoService(
            BenutzerRepository benutzerRepository,
            PasskeyRepository passkeyRepository,
            IntervallEinstellungRepository intervallEinstellungRepository,
            WorkoutRepository workoutRepository,
            WorkoutEinladungRepository workoutEinladungRepository,
            AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository,
            Anmeldesitzung anmeldesitzung,
            Uhr uhr
    ) {
        this.benutzerRepository = benutzerRepository;
        this.passkeyRepository = passkeyRepository;
        this.intervallEinstellungRepository = intervallEinstellungRepository;
        this.workoutRepository = workoutRepository;
        this.workoutEinladungRepository = workoutEinladungRepository;
        this.ausstehendeRegistrierungRepository = ausstehendeRegistrierungRepository;
        this.anmeldesitzung = anmeldesitzung;
        this.uhr = uhr;
    }

    public DsgvoExportDaten exportiereDaten(BenutzerId benutzerId) {
        Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        Benutzer benutzer = benutzerRepository.findeNachId(benutzerId)
                .orElseThrow(BenutzerNichtGefundenException::new);

        List<Passkey> passkeys = passkeyRepository.findeAlleNachBenutzerId(benutzerId);
        IntervallEinstellung timerEinstellung = intervallEinstellungRepository
                .findeNachBenutzerId(benutzerId)
                .orElse(null);

        List<Workout> eigeneWorkouts = workoutRepository.findeAlleNachOwner(benutzerId, null);
        List<WorkoutId> eigeneWorkoutIds = eigeneWorkouts.stream().map(Workout::id).toList();
        List<WorkoutEinladung> ausgehendeEinladungen = workoutEinladungRepository
                .findeAlleNachWorkoutIds(eigeneWorkoutIds);

        List<WorkoutEinladung> erhalteneEinladungen = workoutEinladungRepository
                .findeAlleNachEingeladenerId(benutzerId);
        List<WorkoutId> geteilteWorkoutIds = erhalteneEinladungen.stream()
                .map(WorkoutEinladung::workoutId)
                .distinct()
                .toList();
        Map<WorkoutId, Workout> geteilteWorkoutsNachId = workoutRepository.findeAlleNachIds(geteilteWorkoutIds)
                .stream()
                .collect(Collectors.toMap(Workout::id, Function.identity()));

        Set<BenutzerId> nachzuladendeBenutzerIds = new HashSet<>();
        ausgehendeEinladungen.forEach(e -> nachzuladendeBenutzerIds.add(e.eingeladenerId()));
        geteilteWorkoutsNachId.values().forEach(w -> nachzuladendeBenutzerIds.add(w.ownerId()));
        Map<BenutzerId, Benutzer> benutzerNachId = benutzerRepository.findeAlleNachIds(nachzuladendeBenutzerIds);

        Map<WorkoutId, List<WorkoutEinladungAnsicht>> einladungenProWorkout = new HashMap<>();
        for (WorkoutEinladung einladung : ausgehendeEinladungen) {
            Benutzer eingeladener = benutzerNachId.get(einladung.eingeladenerId());
            if (eingeladener != null) {
                einladungenProWorkout
                        .computeIfAbsent(einladung.workoutId(), ignored -> new ArrayList<>())
                        .add(new WorkoutEinladungAnsicht(einladung, eingeladener.benutzername()));
            }
        }

        List<DsgvoExportDaten.WorkoutExport> workoutExporte = eigeneWorkouts.stream()
                .map(workout -> new DsgvoExportDaten.WorkoutExport(
                        workout,
                        List.copyOf(einladungenProWorkout.getOrDefault(workout.id(), List.of()))
                ))
                .toList();

        List<DsgvoExportDaten.ErhalteneEinladungExport> erhalteneExporte = new ArrayList<>();
        for (WorkoutEinladung einladung : erhalteneEinladungen) {
            Workout workout = geteilteWorkoutsNachId.get(einladung.workoutId());
            if (workout == null) {
                continue;
            }
            Benutzer owner = benutzerNachId.get(workout.ownerId());
            if (owner == null) {
                continue;
            }
            erhalteneExporte.add(new DsgvoExportDaten.ErhalteneEinladungExport(
                    einladung,
                    workout.titel(),
                    owner.id(),
                    owner.benutzername()
            ));
        }

        return new DsgvoExportDaten(
                uhr.jetzt(),
                benutzer,
                passkeys,
                timerEinstellung,
                workoutExporte,
                List.copyOf(erhalteneExporte)
        );
    }

    public DsgvoExportDaten exportiereDatenFuerAktuellenBenutzer() {
        return exportiereDaten(erforderlicherBenutzerId());
    }

    /**
     * Löscht ein Benutzerkonto inkl. aller abhängigen Daten (Passkeys, Timer-Einstellung, Workouts,
     * Übungen, Sätze, ausgehende/eingehende Einladungen per ON DELETE CASCADE sowie ausstehende Registrierungen)
     * in einer Transaktion.
     */
    @Transactional
    public void loescheKonto(BenutzerId benutzerId) {
        Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        Benutzer benutzer = benutzerRepository.findeNachId(benutzerId)
                .orElseThrow(BenutzerNichtGefundenException::new);

        ausstehendeRegistrierungRepository.loeschenNachBenutzerId(benutzerId);
        ausstehendeRegistrierungRepository.loeschenNachBenutzername(benutzer.benutzername());
        boolean geloescht = benutzerRepository.loeschen(benutzerId);
        if (!geloescht) {
            throw new BenutzerNichtGefundenException();
        }
    }

    @Transactional
    public void loescheKontoFuerAktuellenBenutzer() {
        BenutzerId akteur = erforderlicherBenutzerId();
        loescheKonto(akteur);
        anmeldesitzung.beenden();
    }

    private BenutzerId erforderlicherBenutzerId() {
        return anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
    }
}
