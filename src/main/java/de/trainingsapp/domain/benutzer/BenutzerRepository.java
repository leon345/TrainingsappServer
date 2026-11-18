package de.trainingsapp.domain.benutzer;

import java.util.Optional;

/** Port: Ablage der Benutzer. */
public interface BenutzerRepository {

    Optional<Benutzer> findeNachId(BenutzerId id);

    Optional<Benutzer> findeNachName(String benutzername);

    /**
     * Legt den Benutzer an.
     *
     * @return {@code false}, wenn der Benutzername bereits vergeben ist
     */
    boolean legeAn(Benutzer benutzer);
}
