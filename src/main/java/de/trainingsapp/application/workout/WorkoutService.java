package de.trainingsapp.application.workout;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Satz;
import de.trainingsapp.domain.workout.SatzId;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Uebung;
import de.trainingsapp.domain.workout.UebungId;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungRepository;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutNichtGefundenException;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.workout.WorkoutTyp;
import de.trainingsapp.domain.workout.WorkoutVersionskonfliktException;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anwendungsservice für Kraft- und Calisthenics-Workouts inkl. Eigentümerprüfung und optimistischer Sperre.
 */
@ApplicationScoped
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final WorkoutEinladungRepository workoutEinladungRepository;
    private final BenutzerRepository benutzerRepository;
    private final Anmeldesitzung anmeldesitzung;
    private final ZugriffsPolicy zugriffsPolicy;
    private final Uhr uhr;

    public WorkoutService(
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

    public List<Workout> listeEigene(BenutzerId ownerId) {
        return listeEigene(ownerId, null);
    }

    public List<Workout> listeEigene(BenutzerId ownerId, WorkoutTyp optionalerTyp) {
        Objects.requireNonNull(ownerId, "ownerId darf nicht null sein");
        return workoutRepository.findeAlleNachOwner(ownerId, optionalerTyp);
    }

    public List<Workout> listeEigeneFuerAktuellenBenutzer(WorkoutTyp optionalerTyp) {
        return listeEigene(erforderlicherBenutzerId(), optionalerTyp);
    }

    public List<GeteiltesWorkoutAnsicht> listeGeteilte(BenutzerId akteur) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Rolle rolle = benutzerRepository.findeNachId(akteur)
                .map(Benutzer::rolle)
                .orElse(null);
        List<WorkoutEinladung> einladungen = workoutEinladungRepository.findeAlleNachEingeladenerId(akteur);
        if (einladungen.isEmpty()) {
            return List.of();
        }
        Map<WorkoutId, Instant> eingeladenAmNachWorkout = new LinkedHashMap<>();
        for (WorkoutEinladung einladung : einladungen) {
            eingeladenAmNachWorkout.putIfAbsent(einladung.workoutId(), einladung.erstelltAm());
        }
        List<Workout> workouts = workoutRepository.findeAlleNachIds(eingeladenAmNachWorkout.keySet()).stream()
                .filter(workout -> zugriffsPolicy.darfWorkoutLesen(akteur, rolle, workout, true))
                .toList();
        if (workouts.isEmpty()) {
            return List.of();
        }
        Set<BenutzerId> ownerIds = workouts.stream()
                .map(Workout::ownerId)
                .collect(Collectors.toSet());
        Map<BenutzerId, Benutzer> besitzerNachId = benutzerRepository.findeAlleNachIds(ownerIds);
        List<GeteiltesWorkoutAnsicht> ergebnis = new ArrayList<>(workouts.size());
        for (Workout workout : workouts) {
            Benutzer owner = besitzerNachId.get(workout.ownerId());
            if (owner != null) {
                ergebnis.add(new GeteiltesWorkoutAnsicht(
                        workout,
                        owner.benutzername(),
                        eingeladenAmNachWorkout.get(workout.id())
                ));
            }
        }
        return ergebnis;
    }

    public List<GeteiltesWorkoutAnsicht> listeGeteilteFuerAktuellenBenutzer() {
        return listeGeteilte(erforderlicherBenutzerId());
    }

    public Workout lese(BenutzerId akteur, WorkoutId id) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(id, "id darf nicht null sein");
        Rolle rolle = benutzerRepository.findeNachId(akteur)
                .map(Benutzer::rolle)
                .orElse(null);
        boolean eingeladen = workoutEinladungRepository.existiertAktiveEinladung(id, akteur);
        return workoutRepository.findeNachId(id)
                .filter(workout -> zugriffsPolicy.darfWorkoutLesen(akteur, rolle, workout, eingeladen))
                .orElseThrow(WorkoutNichtGefundenException::new);
    }

    public Workout leseFuerAktuellenBenutzer(WorkoutId id) {
        return lese(erforderlicherBenutzerId(), id);
    }

    @Transactional
    public Workout erstelle(BenutzerId ownerId, WorkoutEingabe eingabe) {
        Objects.requireNonNull(ownerId, "ownerId darf nicht null sein");
        if (eingabe == null) {
            throw new DomainException(Workout.FEHLERCODE, "Workout-Eingabe darf nicht fehlen.");
        }
        Instant jetzt = uhr.jetzt();
        Sichtbarkeit sichtbarkeit = eingabe.sichtbarkeit() != null ? eingabe.sichtbarkeit() : Sichtbarkeit.PRIVAT;
        List<Uebung> uebungen = baueUebungen(eingabe.uebungen());

        Workout neu = new Workout(
                WorkoutId.neu(),
                ownerId,
                eingabe.typ(),
                eingabe.titel(),
                eingabe.startZeit(),
                eingabe.dauerSekunden(),
                eingabe.notiz(),
                sichtbarkeit,
                Herkunft.MANUELL,
                null,
                uebungen,
                jetzt,
                jetzt,
                0L
        );
        workoutRepository.speichern(neu);
        return neu;
    }

    @Transactional
    public Workout erstelleFuerAktuellenBenutzer(WorkoutEingabe eingabe) {
        return erstelle(erforderlicherBenutzerId(), eingabe);
    }

    @Transactional
    public Workout aktualisiere(BenutzerId akteur, WorkoutId id, long erwarteteVersion, WorkoutEingabe eingabe) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(id, "id darf nicht null sein");
        if (eingabe == null) {
            throw new DomainException(Workout.FEHLERCODE, "Workout-Eingabe darf nicht fehlen.");
        }
        Workout vorhanden = workoutRepository.findeNachId(id)
                .filter(workout -> zugriffsPolicy.darfWorkoutBearbeiten(akteur, workout))
                .orElseThrow(WorkoutNichtGefundenException::new);

        if (vorhanden.version() != erwarteteVersion) {
            throw new WorkoutVersionskonfliktException();
        }

        Sichtbarkeit sichtbarkeit = eingabe.sichtbarkeit() != null
                ? eingabe.sichtbarkeit()
                : vorhanden.sichtbarkeit();
        List<Uebung> uebungen = baueUebungen(eingabe.uebungen());
        Workout aktualisiert = vorhanden.aktualisieren(
                eingabe.typ(),
                eingabe.titel(),
                eingabe.startZeit(),
                eingabe.dauerSekunden(),
                eingabe.notiz(),
                sichtbarkeit,
                uebungen,
                uhr.jetzt()
        );
        return workoutRepository.aktualisieren(aktualisiert, erwarteteVersion);
    }

    @Transactional
    public Workout aktualisiereFuerAktuellenBenutzer(WorkoutId id, long erwarteteVersion, WorkoutEingabe eingabe) {
        return aktualisiere(erforderlicherBenutzerId(), id, erwarteteVersion, eingabe);
    }

    @Transactional
    public Workout aendereSichtbarkeit(BenutzerId akteur, WorkoutId id, Sichtbarkeit sichtbarkeit) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(id, "id darf nicht null sein");
        if (sichtbarkeit == null) {
            throw new DomainException(Workout.FEHLERCODE, "Die Sichtbarkeit darf nicht fehlen.");
        }
        Workout vorhanden = workoutRepository.findeNachId(id)
                .filter(workout -> zugriffsPolicy.darfWorkoutBearbeiten(akteur, workout))
                .orElseThrow(WorkoutNichtGefundenException::new);
        Workout aktualisiert = vorhanden.mitSichtbarkeit(sichtbarkeit, uhr.jetzt());
        return workoutRepository.aktualisiereSichtbarkeit(aktualisiert, vorhanden.version());
    }

    @Transactional
    public Workout aendereSichtbarkeitFuerAktuellenBenutzer(WorkoutId id, Sichtbarkeit sichtbarkeit) {
        return aendereSichtbarkeit(erforderlicherBenutzerId(), id, sichtbarkeit);
    }

    @Transactional
    public void loesche(BenutzerId akteur, WorkoutId id) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(id, "id darf nicht null sein");
        Workout vorhanden = workoutRepository.findeNachId(id)
                .filter(workout -> zugriffsPolicy.darfWorkoutBearbeiten(akteur, workout))
                .orElseThrow(WorkoutNichtGefundenException::new);
        workoutRepository.loeschen(vorhanden.id());
    }

    @Transactional
    public void loescheFuerAktuellenBenutzer(WorkoutId id) {
        loesche(erforderlicherBenutzerId(), id);
    }

    public static List<Uebung> baueUebungen(List<UebungEingabe> eingaben) {
        if (eingaben == null || eingaben.isEmpty()) {
            return List.of();
        }
        List<Uebung> uebungen = new ArrayList<>(eingaben.size());
        for (int i = 0; i < eingaben.size(); i++) {
            UebungEingabe uebungEingabe = eingaben.get(i);
            if (uebungEingabe == null) {
                throw new DomainException(Uebung.FEHLERCODE, "Übungseintrag darf nicht null sein.");
            }
            List<Satz> saetze = baueSaetze(uebungEingabe.saetze());
            uebungen.add(new Uebung(
                    UebungId.neu(),
                    i,
                    uebungEingabe.name(),
                    uebungEingabe.variante(),
                    saetze
            ));
        }
        return List.copyOf(uebungen);
    }

    private static List<Satz> baueSaetze(List<SatzEingabe> eingaben) {
        if (eingaben == null || eingaben.isEmpty()) {
            return List.of();
        }
        List<Satz> saetze = new ArrayList<>(eingaben.size());
        for (int i = 0; i < eingaben.size(); i++) {
            SatzEingabe satzEingabe = eingaben.get(i);
            if (satzEingabe == null) {
                throw new DomainException(Satz.FEHLERCODE, "Satzeintrag darf nicht null sein.");
            }
            saetze.add(new Satz(
                    SatzId.neu(),
                    i,
                    satzEingabe.wiederholungen(),
                    satzEingabe.dauerSekunden(),
                    satzEingabe.gewichtKg(),
                    satzEingabe.pauseSekunden()
            ));
        }
        return List.copyOf(saetze);
    }

    private BenutzerId erforderlicherBenutzerId() {
        return anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
    }
}
