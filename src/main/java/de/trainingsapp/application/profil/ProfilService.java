package de.trainingsapp.application.profil;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerNichtGefundenException;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;

/**
 * Anwendungsservice für das Lesen öffentlicher Profil-Workouts eines Benutzers (E4, E6).
 */
@ApplicationScoped
public class ProfilService {

    private final BenutzerRepository benutzerRepository;
    private final WorkoutRepository workoutRepository;
    private final Anmeldesitzung anmeldesitzung;
    private final ZugriffsPolicy zugriffsPolicy;

    public ProfilService(
            BenutzerRepository benutzerRepository,
            WorkoutRepository workoutRepository,
            Anmeldesitzung anmeldesitzung,
            ZugriffsPolicy zugriffsPolicy
    ) {
        this.benutzerRepository = benutzerRepository;
        this.workoutRepository = workoutRepository;
        this.anmeldesitzung = anmeldesitzung;
        this.zugriffsPolicy = zugriffsPolicy;
    }

    public List<Workout> listeOeffentlicheWorkouts(BenutzerId akteur, BenutzerId profilBesitzerId) {
        Objects.requireNonNull(akteur, "akteur darf nicht null sein");
        Objects.requireNonNull(profilBesitzerId, "profilBesitzerId darf nicht null sein");

        Rolle rolle = benutzerRepository.findeNachId(akteur)
                .map(Benutzer::rolle)
                .orElse(null);
        if (!zugriffsPolicy.darfOeffentlichesProfilLesen(akteur, rolle, profilBesitzerId)) {
            throw new BenutzerNichtGefundenException();
        }
        benutzerRepository.findeNachId(profilBesitzerId)
                .orElseThrow(BenutzerNichtGefundenException::new);
        return workoutRepository.findeOeffentlicheNachOwner(profilBesitzerId);
    }

    public List<Workout> listeOeffentlicheWorkoutsFuerAktuellenBenutzer(BenutzerId profilBesitzerId) {
        BenutzerId akteur = anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
        return listeOeffentlicheWorkouts(akteur, profilBesitzerId);
    }
}
