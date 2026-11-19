package de.trainingsapp.application.benutzer;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerNichtGefundenException;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Anwendungsservice für die exakte Benutzernamenssuche zur Vorbereitung von Workout-Einladungen.
 */
@ApplicationScoped
public class BenutzerSucheService {

    private final BenutzerRepository benutzerRepository;
    private final Anmeldesitzung anmeldesitzung;

    public BenutzerSucheService(BenutzerRepository benutzerRepository, Anmeldesitzung anmeldesitzung) {
        this.benutzerRepository = benutzerRepository;
        this.anmeldesitzung = anmeldesitzung;
    }

    public Benutzer sucheExaktFuerAktuellenBenutzer(String nameEingabe) {
        anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
        Benutzername normalisiert = new Benutzername(nameEingabe);
        return benutzerRepository.findeNachBenutzername(normalisiert)
                .orElseThrow(BenutzerNichtGefundenException::new);
    }
}
