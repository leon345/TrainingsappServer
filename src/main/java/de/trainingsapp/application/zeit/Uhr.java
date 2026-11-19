package de.trainingsapp.application.zeit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Port für deterministische Zeitabfragen (UTC).
 */
public interface Uhr {

    Instant jetzt();

    default LocalDate heuteUtc() {
        return LocalDate.ofInstant(jetzt(), ZoneOffset.UTC);
    }
}
