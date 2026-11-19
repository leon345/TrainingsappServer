package de.trainingsapp.infrastructure.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.IntervallEinstellungRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheIntervallEinstellungRepository
        implements IntervallEinstellungRepository, PanacheRepositoryBase<TimerEinstellungEntity, UUID> {

    @Override
    public Optional<IntervallEinstellung> findeNachBenutzerId(BenutzerId benutzerId) {
        return findByIdOptional(benutzerId.wert()).map(TimerEinstellungEntity::zuDomain);
    }

    @Override
    public void speichern(BenutzerId benutzerId, IntervallEinstellung einstellung, Instant geaendertAm) {
        TimerEinstellungEntity vorhanden = findById(benutzerId.wert());
        if (vorhanden == null) {
            persist(TimerEinstellungEntity.ausDomain(benutzerId, einstellung, geaendertAm));
        } else {
            vorhanden.trainingSekunden = einstellung.trainingSekunden();
            vorhanden.pauseSekunden = einstellung.pauseSekunden();
            vorhanden.geaendertAm = geaendertAm;
        }
        flush();
    }
}
