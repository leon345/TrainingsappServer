package de.trainingsapp.application.timer;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import de.trainingsapp.domain.timer.IntervallEinstellungRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;

/**
 * Anwendungsservice für die benutzerspezifische Intervalltimer-Einstellung.
 */
@ApplicationScoped
public class TimerEinstellungService {

    private final IntervallEinstellungRepository repository;
    private final Anmeldesitzung anmeldesitzung;
    private final Uhr uhr;

    public TimerEinstellungService(
            IntervallEinstellungRepository repository,
            Anmeldesitzung anmeldesitzung,
            Uhr uhr
    ) {
        this.repository = repository;
        this.anmeldesitzung = anmeldesitzung;
        this.uhr = uhr;
    }

    public TimerEinstellungAnsicht leseOderStandard(BenutzerId benutzerId) {
        Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        return repository.findeNachBenutzerId(benutzerId)
                .map(einstellung -> new TimerEinstellungAnsicht(einstellung, true))
                .orElseGet(() -> new TimerEinstellungAnsicht(IntervallEinstellung.STANDARD, false));
    }

    public TimerEinstellungAnsicht leseFuerAktuellenBenutzer() {
        return leseOderStandard(erforderlicherBenutzerId());
    }

    @Transactional
    public TimerEinstellungAnsicht speichere(BenutzerId benutzerId, IntervallEinstellung einstellung) {
        Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        Objects.requireNonNull(einstellung, "einstellung darf nicht null sein");
        repository.speichern(benutzerId, einstellung, uhr.jetzt());
        return new TimerEinstellungAnsicht(einstellung, true);
    }

    @Transactional
    public TimerEinstellungAnsicht speichereFuerAktuellenBenutzer(int trainingSekunden, int pauseSekunden) {
        IntervallEinstellung einstellung = new IntervallEinstellung(trainingSekunden, pauseSekunden);
        return speichere(erforderlicherBenutzerId(), einstellung);
    }

    private BenutzerId erforderlicherBenutzerId() {
        return anmeldesitzung.aktuellerBenutzer()
                .orElseThrow(() -> new DomainNichtAutorisiertException(
                        "NICHT_ANGEMELDET",
                        "Für diese Aktion ist eine Anmeldung erforderlich."
                ));
    }
}
