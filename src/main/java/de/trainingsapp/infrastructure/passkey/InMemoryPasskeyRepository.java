package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uebergangs-Adapter (E9): Passkeys nur im Arbeitsspeicher. Alle Zugriffe sind
 * synchronisiert, damit die Loeschsperre auch bei parallelen Loeschungen haelt.
 * In S-12 entspricht das einer Transaktion mit Sperre auf die Passkeys des Benutzers.
 */
@DefaultBean
@ApplicationScoped
public class InMemoryPasskeyRepository implements PasskeyRepository {

    private final Map<PasskeyId, Passkey> passkeys = new HashMap<>();

    @Override
    public synchronized Optional<Passkey> findeNachId(PasskeyId id) {
        return Optional.ofNullable(passkeys.get(id));
    }

    @Override
    public synchronized List<Passkey> findeFuer(BenutzerId benutzerId) {
        return passkeys.values().stream().filter(p -> p.benutzerId().equals(benutzerId)).toList();
    }

    @Override
    public synchronized boolean fuegeHinzu(Passkey passkey) {
        return passkeys.putIfAbsent(passkey.id(), passkey) == null;
    }

    @Override
    public synchronized void aktualisiereZaehler(PasskeyId id, long zaehler) {
        passkeys.computeIfPresent(id, (k, p) -> p.mitZaehler(zaehler));
    }

    @Override
    public synchronized LoeschErgebnis loescheWennNichtLetzter(BenutzerId benutzerId, PasskeyId id) {
        Passkey passkey = passkeys.get(id);
        if (passkey == null || !passkey.benutzerId().equals(benutzerId)) {
            return LoeschErgebnis.NICHT_GEFUNDEN;
        }
        if (findeFuer(benutzerId).size() <= 1) {
            return LoeschErgebnis.LETZTER_PASSKEY;
        }
        passkeys.remove(id);
        return LoeschErgebnis.GELOESCHT;
    }
}
