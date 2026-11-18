package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;

/** Uebersetzt zwischen {@link AusstehendeRegistrierung} und {@link AusstehendeRegistrierungEntity}. */
final class AusstehendeRegistrierungMapper {

    private AusstehendeRegistrierungMapper() {
    }

    static AusstehendeRegistrierung zuDomain(AusstehendeRegistrierungEntity entity) {
        return new AusstehendeRegistrierung(new BenutzerId(entity.id), new Benutzername(entity.benutzername),
                entity.gueltigBis);
    }

    static AusstehendeRegistrierungEntity zuEntity(AusstehendeRegistrierung registrierung) {
        AusstehendeRegistrierungEntity entity = new AusstehendeRegistrierungEntity();
        entity.id = registrierung.vorgesehen().wert();
        entity.benutzername = registrierung.benutzername().wert();
        entity.gueltigBis = registrierung.gueltigBis();
        return entity;
    }
}
