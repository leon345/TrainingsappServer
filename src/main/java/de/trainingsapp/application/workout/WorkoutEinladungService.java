package de.trainingsapp.application.workout;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerNichtGefundenException;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.workout.EinladungExistiertBereitsException;
import de.trainingsapp.domain.workout.EinladungNichtGefundenException;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungRepository;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutNichtGefundenException;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anwendungsservice zur Verwaltung von Einladungen an eigenen Workouts (ausschließlich Besitzer).
 */
@ApplicationScoped
public class WorkoutEinladungService {

    public static final String FEHLERCODE_SELBSTEINLADUNG = "SELBSTEINLADUNG_NICHT_ERLAUBT";

    private final WorkoutRepository workoutRepository;
    private final WorkoutEinladungRepository workoutEinladungRepository;
    private final BenutzerRepository benutzerRepository;
    private final Anmeldesitzung anmeldesitzung;
    private final ZugriffsPolicy zugriffsPolicy;
    private final Uhr uhr;

    public WorkoutEinladungService(
            WorkoutRepository workoutRepository,
            WorkoutEinladungRepository workoutEinladungRepository,
            BenutzerRepository benutzerRepository,
            Anmeldesitzung anmeldesitzung,
            ZugriffsPolicy zugriffsPolicy,
            Uhr uhr
    ) {
        this.workoutRepository = workoutRepository;
        this.workoutEinladungRepository = workoutEinladungRepository;
        this.benutzerRepository = benutzerRepository;
        this.anmeldesitzung = anmeldesitzung;
        this.zugriffsPolicy = zugriffsPolicy;
        this.uhr = uhr;
    }

    public List<WorkoutEinladungAnsicht> listeFuerWorkout(BenutzerId akteur, WorkoutId workoutId) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(workoutId, "workoutId darf nicht null sein");
        erforderlichesEigenesWorkout(akteur, workoutId);

        List<WorkoutEinladung> einladungen = workoutEinladungRepository.findeAlleNachWorkoutId(workoutId);
        if (einladungen.isEmpty()) {
            return List.of();
        }
        Set<BenutzerId> eingeladenenIds = einladungen.stream()
                .map(WorkoutEinladung::eingeladenerId)
                .collect(Collectors.toSet());
        Map<BenutzerId, Benutzer> benutzerNachId = benutzerRepository.findeAlleNachIds(eingeladenenIds);

        List<WorkoutEinladungAnsicht> ergebnis = new ArrayList<>(einladungen.size());
        for (WorkoutEinladung einladung : einladungen) {
            Benutzer eingeladener = benutzerNachId.get(einladung.eingeladenerId());
            if (eingeladener != null) {
                ergebnis.add(new WorkoutEinladungAnsicht(einladung, eingeladener.benutzername()));
            }
        }
        return ergebnis;
    }

    public List<WorkoutEinladungAnsicht> listeFuerWorkoutFuerAktuellenBenutzer(WorkoutId workoutId) {
        return listeFuerWorkout(erforderlicherBenutzerId(), workoutId);
    }

    @Transactional
    public WorkoutEinladungAnsicht einladen(BenutzerId akteur, WorkoutId workoutId, String benutzernameEingabe) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(workoutId, "workoutId darf nicht null sein");
        Workout workout = erforderlichesEigenesWorkout(akteur, workoutId);

        Benutzername zielName = new Benutzername(benutzernameEingabe);
        Benutzer zielBenutzer = benutzerRepository.findeNachBenutzername(zielName)
                .orElseThrow(BenutzerNichtGefundenException::new);

        if (zielBenutzer.id().equals(workout.ownerId())) {
            throw new DomainException(
                    FEHLERCODE_SELBSTEINLADUNG,
                    "Du kannst dich nicht selbst zu deinem eigenen Workout einladen."
            );
        }
        if (workoutEinladungRepository.existiertAktiveEinladung(workoutId, zielBenutzer.id())) {
            throw new EinladungExistiertBereitsException();
        }

        WorkoutEinladung neu = WorkoutEinladung.aktiv(workoutId, zielBenutzer.id(), uhr.jetzt());
        workoutEinladungRepository.speichern(neu);
        return new WorkoutEinladungAnsicht(neu, zielBenutzer.benutzername());
    }

    @Transactional
    public WorkoutEinladungAnsicht einladenFuerAktuellenBenutzer(WorkoutId workoutId, String benutzernameEingabe) {
        return einladen(erforderlicherBenutzerId(), workoutId, benutzernameEingabe);
    }

    @Transactional
    public void entfernen(BenutzerId akteur, WorkoutId workoutId, BenutzerId eingeladenerId) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(workoutId, "workoutId darf nicht null sein");
        Objects.requireNonNull(eingeladenerId, "eingeladenerId darf nicht null sein");
        erforderlichesEigenesWorkout(akteur, workoutId);

        boolean geloescht = workoutEinladungRepository.loeschenNachWorkoutUndEingeladener(workoutId, eingeladenerId);
        if (!geloescht) {
            throw new EinladungNichtGefundenException();
        }
    }

    @Transactional
    public void entfernenFuerAktuellenBenutzer(WorkoutId workoutId, BenutzerId eingeladenerId) {
        entfernen(erforderlicherBenutzerId(), workoutId, eingeladenerId);
    }

    private Workout erforderlichesEigenesWorkout(BenutzerId akteur, WorkoutId workoutId) {
        return workoutRepository.findeNachId(workoutId)
                .filter(workout -> zugriffsPolicy.darfWorkoutBearbeiten(akteur, workout))
                .orElseThrow(WorkoutNichtGefundenException::new);
    }

    private BenutzerId erforderlicherBenutzerId() {
        return anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
    }
}
