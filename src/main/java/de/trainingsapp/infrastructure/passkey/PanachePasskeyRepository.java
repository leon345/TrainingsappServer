package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Datenbank-Adapter fuer {@link PasskeyRepository} (Tabelle {@code passkey}, V2).
 * Jede Methode laeuft in einer eigenen Transaktion (siehe {@code PanacheBenutzerRepository}).
 */
@ApplicationScoped
public class PanachePasskeyRepository implements PasskeyRepository, PanacheRepositoryBase<PasskeyEntity, UUID> {

    @Override
    @Transactional
    public Optional<Passkey> findeNachId(PasskeyId id) {
        Objects.requireNonNull(id, "id");
        return find("credentialId", id.wert()).firstResultOptional().map(PasskeyMapper::zuDomain);
    }

    @Override
    @Transactional
    public List<Passkey> findeFuer(BenutzerId benutzerId) {
        Objects.requireNonNull(benutzerId, "benutzerId");
        return list("benutzerId", Sort.by("erstelltAm").and("id"), benutzerId.wert()).stream()
                .map(PasskeyMapper::zuDomain)
                .toList();
    }

    /** {@code ON CONFLICT DO NOTHING}: eine schon registrierte Credential-ID ergibt {@code false}. */
    @Override
    @Transactional
    public boolean fuegeHinzu(Passkey passkey) {
        PasskeyEntity neu = PasskeyMapper.zuNeuerEntity(passkey);
        int angelegt = getEntityManager().createNativeQuery("""
                        INSERT INTO passkey (id, credential_id, benutzer_id, aaguid, public_key, algorithmus,
                                             zaehler, erstellt_am)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                        ON CONFLICT DO NOTHING""")
                .setParameter(1, neu.id)
                .setParameter(2, neu.credentialId)
                .setParameter(3, neu.benutzerId)
                .setParameter(4, neu.aaguid)
                .setParameter(5, neu.publicKey)
                .setParameter(6, neu.algorithmus)
                .setParameter(7, neu.zaehler)
                .setParameter(8, neu.erstelltAm)
                .executeUpdate();
        return angelegt == 1;
    }

    @Override
    @Transactional
    public void aktualisiereZaehler(PasskeyId id, long zaehler) {
        Objects.requireNonNull(id, "id");
        update("zaehler = ?1 where credentialId = ?2", zaehler, id.wert());
    }

    /**
     * Sperrt die Zeile des Benutzers ({@code SELECT ... FOR UPDATE}), damit parallele Loeschungen
     * desselben Benutzers nacheinander laufen und nie den letzten Passkey entfernen.
     */
    @Override
    @Transactional
    public LoeschErgebnis loescheWennNichtLetzter(BenutzerId benutzerId, PasskeyId id) {
        Objects.requireNonNull(benutzerId, "benutzerId");
        Objects.requireNonNull(id, "id");
        List<?> gesperrt = getEntityManager()
                .createNativeQuery("SELECT id FROM benutzer WHERE id = ?1 FOR UPDATE")
                .setParameter(1, benutzerId.wert())
                .getResultList();
        if (gesperrt.isEmpty()) {
            return LoeschErgebnis.NICHT_GEFUNDEN;
        }
        Optional<PasskeyEntity> passkey = find("credentialId = ?1 and benutzerId = ?2", id.wert(), benutzerId.wert())
                .firstResultOptional();
        if (passkey.isEmpty()) {
            return LoeschErgebnis.NICHT_GEFUNDEN;
        }
        if (count("benutzerId", benutzerId.wert()) <= 1) {
            return LoeschErgebnis.LETZTER_PASSKEY;
        }
        delete(passkey.get());
        return LoeschErgebnis.GELOESCHT;
    }
}
