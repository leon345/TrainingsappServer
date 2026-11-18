package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Datenbank-Adapter fuer {@link AusstehendeRegistrierungRepository} (Tabelle {@code ausstehende_registrierung}, V3). */
@ApplicationScoped
public class PanacheAusstehendeRegistrierungRepository
        implements AusstehendeRegistrierungRepository, PanacheRepositoryBase<AusstehendeRegistrierungEntity, UUID> {

    /**
     * Raeumt Abgelaufenes ab und legt die Reservierung mit {@code ON CONFLICT DO NOTHING} an.
     * Bei parallelen Aufrufen fuer denselben Namen gewinnt der erste; alle erhalten dessen Reservierung.
     */
    @Override
    @Transactional
    public AusstehendeRegistrierung reserviere(AusstehendeRegistrierung neu, Instant jetzt) {
        Objects.requireNonNull(neu, "neu");
        Objects.requireNonNull(jetzt, "jetzt");
        delete("gueltigBis <= ?1", jetzt);
        AusstehendeRegistrierungEntity entity = AusstehendeRegistrierungMapper.zuEntity(neu);
        getEntityManager().createNativeQuery("""
                        INSERT INTO ausstehende_registrierung (id, benutzername, gueltig_bis)
                        VALUES (?1, ?2, ?3)
                        ON CONFLICT DO NOTHING""")
                .setParameter(1, entity.id)
                .setParameter(2, entity.benutzername)
                .setParameter(3, entity.gueltigBis)
                .executeUpdate();
        return find("benutzername", neu.benutzername().wert()).firstResultOptional()
                .map(AusstehendeRegistrierungMapper::zuDomain)
                .orElseThrow(() -> new IllegalStateException("Reservierung nach dem Anlegen nicht gefunden"));
    }

    @Override
    @Transactional
    public Optional<AusstehendeRegistrierung> entnimm(Benutzername benutzername, Instant jetzt) {
        Objects.requireNonNull(benutzername, "benutzername");
        Objects.requireNonNull(jetzt, "jetzt");
        Optional<AusstehendeRegistrierungEntity> vorhanden = find("benutzername", benutzername.wert())
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
        vorhanden.ifPresent(this::delete);
        return vorhanden.map(AusstehendeRegistrierungMapper::zuDomain).filter(r -> r.istGueltigUm(jetzt));
    }
}
