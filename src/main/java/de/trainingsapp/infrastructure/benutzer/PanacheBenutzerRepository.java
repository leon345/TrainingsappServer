package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Datenbank-Adapter fuer {@link BenutzerRepository} (Tabelle {@code benutzer}, V2).
 *
 * <p>Transaktionen (E26): Schreibmethoden verlangen eine laufende Transaktion ({@code MANDATORY}),
 * die der Use-Case ueber den Port {@code application.Transaktion} oeffnet. Lesemethoden oeffnen bei
 * Bedarf eine eigene, weil die WebAuthn-Extension sie auch ausserhalb eines Request-Kontexts aufruft.
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
    public Optional<Benutzer> findeNachName(Benutzername benutzername) {
        Objects.requireNonNull(benutzername, "benutzername");
        return find("benutzername", benutzername.wert()).firstResultOptional()
                .map(BenutzerMapper::zuDomain);
    }

    /**
     * Legt den Benutzer an, ohne Ausnahme bei Konflikten: {@code ON CONFLICT DO NOTHING} greift
     * fuer die ID und fuer den Namen (UNIQUE, nur Kleinschreibung gespeichert), auch bei parallelen
     * Registrierungen.
     */
    @Override
    @Transactional(TxType.MANDATORY)
    public boolean legeAn(Benutzer benutzer) {
        BenutzerEntity neu = BenutzerMapper.zuEntity(benutzer);
        int angelegt = getEntityManager()
                .createNativeQuery("INSERT INTO benutzer (id, benutzername) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                .setParameter(1, neu.id)
                .setParameter(2, neu.benutzername)
                .executeUpdate();
        return angelegt == 1;
    }

    @Override
    @Transactional(TxType.MANDATORY)
    public int naechstePasskeyNummer(BenutzerId id) {
        Objects.requireNonNull(id, "id");
        Object nummer = getEntityManager().createNativeQuery("""
                        UPDATE benutzer SET letzte_passkey_nummer = letzte_passkey_nummer + 1
                        WHERE id = ?1 RETURNING letzte_passkey_nummer""")
                .setParameter(1, id.wert())
                .getSingleResult();
        return ((Number) nummer).intValue();
    }
}
