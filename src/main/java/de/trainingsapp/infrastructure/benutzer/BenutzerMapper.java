package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;

/** Uebersetzt zwischen {@link Benutzer} und {@link BenutzerEntity}. */
final class BenutzerMapper {

    private BenutzerMapper() {
    }

    static Benutzer zuDomain(BenutzerEntity entity) {
        return new Benutzer(new BenutzerId(entity.id), entity.benutzername);
    }

    static BenutzerEntity zuEntity(Benutzer benutzer) {
        BenutzerEntity entity = new BenutzerEntity();
        entity.id = benutzer.id().wert();
        entity.benutzername = benutzer.benutzername();
        return entity;
    }
}
