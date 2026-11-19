package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheBenutzerRepository
        implements BenutzerRepository, PanacheRepositoryBase<BenutzerEntity, UUID> {

    @Override
    public Optional<Benutzer> findeNachId(BenutzerId id) {
        return findByIdOptional(id.wert()).map(BenutzerEntity::zuDomain);
    }

    @Override
    public Optional<Benutzer> findeNachBenutzername(Benutzername benutzername) {
        return find("benutzername", benutzername.wert())
                .firstResultOptional()
                .map(BenutzerEntity::zuDomain);
    }

    @Override
    public boolean existiertBenutzername(Benutzername benutzername) {
        return count("benutzername", benutzername.wert()) > 0;
    }

    @Override
    public void speichern(Benutzer benutzer) {
        persist(BenutzerEntity.ausDomain(benutzer));
        flush();
    }

    @Override
    public void aktualisieren(Benutzer benutzer) {
        BenutzerEntity entity = findById(benutzer.id().wert());
        if (entity == null) {
            throw new IllegalStateException("Benutzer zum Aktualisieren nicht gefunden: " + benutzer.id().alsText());
        }
        entity.benutzername = benutzer.benutzername().wert();
        entity.rolle = benutzer.rolle();
        entity.naechstePasskeyNummer = benutzer.naechstePasskeyNummer();
        flush();
    }
}
