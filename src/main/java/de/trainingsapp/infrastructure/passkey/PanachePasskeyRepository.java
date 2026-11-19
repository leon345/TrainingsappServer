package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanachePasskeyRepository
        implements PasskeyRepository, PanacheRepositoryBase<PasskeyEntity, UUID> {

    @Override
    public Optional<Passkey> findeNachId(PasskeyId id) {
        return findByIdOptional(id.wert()).map(PasskeyEntity::zuDomain);
    }

    @Override
    public Optional<Passkey> findeNachCredentialId(String credentialId) {
        return find("credentialId", credentialId)
                .firstResultOptional()
                .map(PasskeyEntity::zuDomain);
    }

    @Override
    public List<Passkey> findeAlleNachBenutzerId(BenutzerId benutzerId) {
        return find("benutzerId", Sort.by("erstelltAm").and("bezeichnung").and("id"), benutzerId.wert())
                .stream()
                .map(PasskeyEntity::zuDomain)
                .toList();
    }

    @Override
    public long zaehleNachBenutzerId(BenutzerId benutzerId) {
        return count("benutzerId", benutzerId.wert());
    }

    @Override
    public void speichern(Passkey passkey) {
        persist(PasskeyEntity.ausDomain(passkey));
        flush();
    }

    @Override
    public void aktualisieren(Passkey passkey) {
        PasskeyEntity entity = findById(passkey.id().wert());
        if (entity == null) {
            throw new IllegalStateException("Passkey zum Aktualisieren nicht gefunden: " + passkey.id().alsText());
        }
        entity.signCount = passkey.signCount();
        entity.bezeichnung = passkey.bezeichnung().wert();
        entity.zuletztVerwendetAm = passkey.zuletztVerwendetAm();
        flush();
    }

    @Override
    public boolean loeschen(PasskeyId id) {
        boolean geloescht = deleteById(id.wert());
        flush();
        return geloescht;
    }
}
