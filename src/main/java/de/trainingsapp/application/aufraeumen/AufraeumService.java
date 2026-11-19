package de.trainingsapp.application.aufraeumen;

import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.benutzer.AusstehendeRegistrierungRepository;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Anwendungsservice für den täglichen Aufräumlauf (E32):
 * - löscht abgelaufene ausstehende Registrierungen (TTL 10 Minuten),
 * - löscht verwaiste Benutzerkonten mit 0 Passkeys älter als 14 Tage.
 * Konten mit mindestens einem Passkey werden niemals automatisch gelöscht.
 * Protokolliert ausschließlich Zähler ohne Personenbezug.
 */
@ApplicationScoped
public class AufraeumService {

    public static final Duration KONTO_OHNE_PASSKEY_FRIST = Duration.ofDays(14);

    private static final Logger LOG = Logger.getLogger(AufraeumService.class);

    private final AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository;
    private final BenutzerRepository benutzerRepository;
    private final Uhr uhr;

    public AufraeumService(
            AusstehendeRegistrierungRepository ausstehendeRegistrierungRepository,
            BenutzerRepository benutzerRepository,
            Uhr uhr
    ) {
        this.ausstehendeRegistrierungRepository = ausstehendeRegistrierungRepository;
        this.benutzerRepository = benutzerRepository;
        this.uhr = uhr;
    }

    @Transactional
    public AufraeumErgebnis aufraeumen() {
        Instant jetzt = uhr.jetzt();
        long geloeschteRegistrierungen = ausstehendeRegistrierungRepository.loescheAbgelaufene(jetzt);
        Instant stichtagKontenOhnePasskey = jetzt.minus(KONTO_OHNE_PASSKEY_FRIST);
        int geloeschteKontenOhnePasskey = benutzerRepository.loescheKontenOhnePasskeyVor(stichtagKontenOhnePasskey);

        LOG.infof(
                "Aufräumlauf abgeschlossen: geloeschteRegistrierungen=%d, geloeschteKontenOhnePasskey=%d",
                geloeschteRegistrierungen,
                geloeschteKontenOhnePasskey
        );
        return new AufraeumErgebnis(geloeschteRegistrierungen, geloeschteKontenOhnePasskey);
    }
}
