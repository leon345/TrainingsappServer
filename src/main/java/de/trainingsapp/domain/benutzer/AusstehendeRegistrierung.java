package de.trainingsapp.domain.benutzer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Kurzlebige ausstehende Registrierung vor Abschluss der ersten Passkey-Zeremonie (TTL 10 Minuten).
 */
public record AusstehendeRegistrierung(
        BenutzerId benutzerId,
        Benutzername benutzername,
        Instant erstelltAm,
        Instant einwilligungAm,
        Instant laeuftAbAm
) {

    public static final Duration TTL = Duration.ofMinutes(10);

    public AusstehendeRegistrierung {
        Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
        Objects.requireNonNull(benutzername, "benutzername darf nicht null sein");
        Objects.requireNonNull(erstelltAm, "erstelltAm darf nicht null sein");
        Objects.requireNonNull(einwilligungAm, "einwilligungAm darf nicht null sein");
        Objects.requireNonNull(laeuftAbAm, "laeuftAbAm darf nicht null sein");
        if (!laeuftAbAm.isAfter(erstelltAm)) {
            throw new IllegalArgumentException("laeuftAbAm muss nach erstelltAm liegen");
        }
    }

    public static AusstehendeRegistrierung neu(BenutzerId benutzerId, Benutzername benutzername, Instant jetzt) {
        Objects.requireNonNull(jetzt, "jetzt darf nicht null sein");
        return new AusstehendeRegistrierung(benutzerId, benutzername, jetzt, jetzt, jetzt.plus(TTL));
    }

    public boolean istAbgelaufen(Instant jetzt) {
        Objects.requireNonNull(jetzt, "jetzt darf nicht null sein");
        return !jetzt.isBefore(laeuftAbAm);
    }
}
