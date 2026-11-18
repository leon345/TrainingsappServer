package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Uebergangs-Adapter (E9): Benutzer nur im Arbeitsspeicher, bis S-12 einen DB-Adapter liefert. */
@DefaultBean
@ApplicationScoped
public class InMemoryBenutzerRepository implements BenutzerRepository {

    private final Map<BenutzerId, Benutzer> nachId = new ConcurrentHashMap<>();
    private final Map<String, Benutzer> nachName = new ConcurrentHashMap<>();

    @Override
    public Optional<Benutzer> findeNachId(BenutzerId id) {
        return Optional.ofNullable(nachId.get(id));
    }

    @Override
    public Optional<Benutzer> findeNachName(String benutzername) {
        return Optional.ofNullable(nachName.get(benutzername));
    }

    @Override
    public synchronized boolean legeAn(Benutzer benutzer) {
        if (nachName.putIfAbsent(benutzer.benutzername(), benutzer) != null) {
            return false;
        }
        nachId.put(benutzer.id(), benutzer);
        return true;
    }
}
