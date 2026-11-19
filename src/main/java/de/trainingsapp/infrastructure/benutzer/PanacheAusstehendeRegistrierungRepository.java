package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheAusstehendeRegistrierungRepository
        implements AusstehendeRegistrierungRepository, PanacheRepositoryBase<AusstehendeRegistrierungEntity, UUID> {

    @Override
    public Optional<AusstehendeRegistrierung> findeNachBenutzername(Benutzername benutzername) {
        return find("benutzername", benutzername.wert())
                .firstResultOptional()
                .map(AusstehendeRegistrierungEntity::zuDomain);
    }

    @Override
    public void speichern(AusstehendeRegistrierung registrierung) {
        persist(AusstehendeRegistrierungEntity.ausDomain(registrierung));
        flush();
    }

    @Override
    public void loeschenNachBenutzerId(BenutzerId benutzerId) {
        deleteById(benutzerId.wert());
        flush();
    }

    @Override
    public void loeschenNachBenutzername(Benutzername benutzername) {
        delete("benutzername", benutzername.wert());
        flush();
    }

    @Override
    public long loescheAbgelaufene(Instant stichtag) {
        long geloescht = delete("laeuftAbAm <= ?1", stichtag);
        flush();
        return geloescht;
    }
}
