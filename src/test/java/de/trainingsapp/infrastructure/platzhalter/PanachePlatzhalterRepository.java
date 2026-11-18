package de.trainingsapp.infrastructure.platzhalter;

import de.trainingsapp.domain.platzhalter.Platzhalter;
import de.trainingsapp.domain.platzhalter.PlatzhalterRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

/**
 * Nur fuer Tests: Adapter fuer den Domain-Port mit Panache im Repository-Muster.
 * Nach aussen gehen nur Domain-Objekte, nie Entities.
 */
@ApplicationScoped
public class PanachePlatzhalterRepository
        implements PlatzhalterRepository, PanacheRepositoryBase<PlatzhalterEntity, UUID> {

    @Override
    @Transactional
    public void speichern(Platzhalter platzhalter) {
        PlatzhalterEntity vorhanden = findById(platzhalter.id());
        if (vorhanden == null) {
            persist(PlatzhalterMapper.zuEntity(platzhalter));
        } else {
            vorhanden.name = platzhalter.name();
        }
    }

    @Override
    public Optional<Platzhalter> findeNachId(UUID id) {
        return findByIdOptional(id).map(PlatzhalterMapper::zuDomain);
    }
}
