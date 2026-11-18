package de.trainingsapp.testhilfe;

import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Stellbare Uhr fuer Tests; ersetzt die Systemuhr, wenn das Testprofil sie als Alternative aktiviert. */
@Alternative
@Singleton
public class StellbareUhr extends Clock {

    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private volatile Instant jetzt = START;

    public void zuruecksetzen() {
        jetzt = START;
    }

    public void vor(Duration dauer) {
        jetzt = jetzt.plus(dauer);
    }

    @Override
    public Instant instant() {
        return jetzt;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
