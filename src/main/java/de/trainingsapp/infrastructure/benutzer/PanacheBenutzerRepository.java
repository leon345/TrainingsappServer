package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public Map<BenutzerId, Benutzer> findeAlleNachIds(Collection<BenutzerId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> uuids = ids.stream().map(BenutzerId::wert).distinct().toList();
        List<BenutzerEntity> entities = getEntityManager().createQuery(
                        "SELECT b FROM BenutzerEntity b WHERE b.id IN :ids",
                        BenutzerEntity.class)
                .setParameter("ids", uuids)
                .getResultList();
        Map<BenutzerId, Benutzer> ergebnis = new LinkedHashMap<>(entities.size());
        for (BenutzerEntity entity : entities) {
            Benutzer benutzer = entity.zuDomain();
            ergebnis.put(benutzer.id(), benutzer);
        }
        return ergebnis;
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
            throw new IllegalStateException("Benutzer zum Aktualisieren nicht gefunden.");
        }
        entity.benutzername = benutzer.benutzername().wert();
        entity.rolle = benutzer.rolle();
        entity.naechstePasskeyNummer = benutzer.naechstePasskeyNummer();
        flush();
    }

    @Override
    public boolean loeschen(BenutzerId id) {
        boolean geloescht = deleteById(id.wert());
        flush();
        getEntityManager().clear();
        return geloescht;
    }

    @Override
    public int loescheKontenOhnePasskeyVor(Instant stichtag) {
        int geloescht = getEntityManager().createQuery(
                        "DELETE FROM BenutzerEntity b WHERE b.erstelltAm < :stichtag "
                                + "AND NOT EXISTS (SELECT 1 FROM PasskeyEntity p WHERE p.benutzerId = b.id)")
                .setParameter("stichtag", stichtag)
                .executeUpdate();
        flush();
        return geloescht;
    }
}
