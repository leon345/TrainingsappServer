package de.trainingsapp.domain.timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Value Object: Einstellung eines Intervalltimers mit Dauer der Trainingsphase
 * und Dauer der Pause. Die Dauern werden sekundengenau gefuehrt und koennen
 * in Sekunden oder Minuten angegeben werden.
 *
 * <p>Regeln:
 * <ul>
 *   <li>Trainingsphase: mindestens {@link #MIN_TRAINING}, hoechstens {@link #MAX_DAUER}</li>
 *   <li>Pause: mindestens {@link #MIN_PAUSE} (0 = keine Pause), hoechstens {@link #MAX_DAUER}</li>
 *   <li>Keine Bruchteile von Sekunden</li>
 * </ul>
 */
public record IntervallEinstellung(Duration training, Duration pause) {

    public static final Duration MIN_TRAINING = Duration.ofSeconds(1);
    public static final Duration MIN_PAUSE = Duration.ZERO;
    public static final Duration MAX_DAUER = Duration.ofHours(24);

    public IntervallEinstellung {
        Objects.requireNonNull(training, "training");
        Objects.requireNonNull(pause, "pause");
        pruefe("training", training, MIN_TRAINING);
        pruefe("pause", pause, MIN_PAUSE);
    }

    /** Erzeugt eine Einstellung aus Sekundenwerten. */
    public static IntervallEinstellung ausSekunden(long trainingSekunden, long pauseSekunden) {
        return new IntervallEinstellung(Duration.ofSeconds(trainingSekunden), Duration.ofSeconds(pauseSekunden));
    }

    /** Erzeugt eine Einstellung aus Minuten- und Sekundenanteilen, z. B. 1 min 30 s. */
    public static IntervallEinstellung ausMinutenUndSekunden(long trainingMinuten, long trainingSekunden,
                                                            long pauseMinuten, long pauseSekunden) {
        return new IntervallEinstellung(
                dauer("training", trainingMinuten, trainingSekunden),
                dauer("pause", pauseMinuten, pauseSekunden));
    }

    public long trainingSekunden() {
        return training.getSeconds();
    }

    public long pauseSekunden() {
        return pause.getSeconds();
    }

    private static Duration dauer(String feld, long minuten, long sekunden) {
        if (minuten < 0 || sekunden < 0) {
            throw new UngueltigeIntervallEinstellungException(feld + " darf nicht negativ sein");
        }
        if (sekunden > 59) {
            throw new UngueltigeIntervallEinstellungException(feld + ": Sekundenanteil muss zwischen 0 und 59 liegen");
        }
        if (minuten > MAX_DAUER.toMinutes()) {
            throw new UngueltigeIntervallEinstellungException(feld + " darf hoechstens " + MAX_DAUER.toMinutes() + " Minuten betragen");
        }
        return Duration.ofMinutes(minuten).plusSeconds(sekunden);
    }

    private static void pruefe(String feld, Duration wert, Duration minimum) {
        if (wert.isNegative()) {
            throw new UngueltigeIntervallEinstellungException(feld + " darf nicht negativ sein");
        }
        if (wert.getNano() != 0) {
            throw new UngueltigeIntervallEinstellungException(feld + " muss in ganzen Sekunden angegeben werden");
        }
        if (wert.compareTo(minimum) < 0) {
            throw new UngueltigeIntervallEinstellungException(feld + " muss mindestens " + minimum.getSeconds() + " Sekunde(n) betragen");
        }
        if (wert.compareTo(MAX_DAUER) > 0) {
            throw new UngueltigeIntervallEinstellungException(feld + " darf hoechstens " + MAX_DAUER.getSeconds() + " Sekunden betragen");
        }
    }
}
