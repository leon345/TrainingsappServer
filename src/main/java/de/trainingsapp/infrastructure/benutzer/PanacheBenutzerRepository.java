package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Datenbank-Adapter fuer {@link BenutzerRepository} (Tabelle {@code benutzer}, V2).
 *
 * <p>Jede Methode laeuft in einer eigenen Transaktion, weil die Application-Schicht keine
 * Transaktionen kennt (E9) und die WebAuthn-Extension Lesezugriffe auch ausserhalb eines
 * Request-Kontexts ausloest.
 */
@ApplicationScoped
public class PanacheBenutzerRepository implements BenutzerRepository, PanacheRepositoryBase<BenutzerEntity, UUID> {

    @Override
    @Transactional
    public Optional<Benutzer> findeNachId(BenutzerId id) {
        Objects.requireNonNull(id, "id");
        return findByIdOptional(id.wert()).map(BenutzerMapper::zuDomain);
    }

    @Override
    @Transactional
    public Optional<Benutzer> findeNachName(String benutzername) {
        Objects.requireNonNull(benutzername, "benutzername");
        return find("lower(benutzername) = lower(?1)", benutzername).firstResultOptional()
                .map(BenutzerMapper::zuDomain);
    }

    /**
     * Legt den Benutzer an, ohne Ausnahme bei Konflikten: {@code ON CONFLICT DO NOTHING} greift
     * fuer die ID und fuer den Namen (eindeutig ohne Gross-/Kleinschreibung), auch bei parallelen
     * Registrierungen.
     */
    @Override
    @Transactional
    public boolean legeAn(Benutzer benutzer) {
        BenutzerEntity neu = BenutzerMapper.zuEntity(benutzer);
        int angelegt = getEntityManager()
                .createNativeQuery("INSERT INTO benutzer (id, benutzername) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                .setParameter(1, neu.id)
                .setParameter(2, neu.benutzername)
                .executeUpdate();
        return angelegt == 1;
    }
}
