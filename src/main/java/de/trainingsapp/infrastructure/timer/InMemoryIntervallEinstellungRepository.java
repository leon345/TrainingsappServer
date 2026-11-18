package de.trainingsapp.infrastructure.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.IntervallEinstellungRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uebergangs-Adapter: haelt die Einstellungen nur im Arbeitsspeicher
 * (gehen beim Neustart verloren).
 *
 * <p>{@link DefaultBean}: Sobald S-12 einen Datenbank-Adapter fuer denselben
 * Port bereitstellt, wird dieser automatisch bevorzugt.
 */
@DefaultBean
@ApplicationScoped
public class InMemoryIntervallEinstellungRepository implements IntervallEinstellungRepository {

    private final Map<BenutzerId, IntervallEinstellung> einstellungen = new ConcurrentHashMap<>();

    @Override
    public Optional<IntervallEinstellung> findeFuer(BenutzerId benutzer) {
        Objects.requireNonNull(benutzer, "benutzer");
        return Optional.ofNullable(einstellungen.get(benutzer));
    }

    @Override
    public void speichere(BenutzerId benutzer, IntervallEinstellung einstellung) {
        Objects.requireNonNull(benutzer, "benutzer");
        Objects.requireNonNull(einstellung, "einstellung");
        einstellungen.put(benutzer, einstellung);
    }
}
