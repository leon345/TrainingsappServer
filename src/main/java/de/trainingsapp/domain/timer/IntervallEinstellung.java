package de.trainingsapp.domain.timer;

import de.trainingsapp.domain.DomainException;

/**
 * Wertobjekt für die Intervalltimer-Einstellung eines Benutzers (alle Dauern in ganzen Sekunden).
 */
public record IntervallEinstellung(int trainingSekunden, int pauseSekunden) {

    public static final String FEHLERCODE = "UNGUELTIGE_TIMER_EINSTELLUNG";
    public static final int MIN_TRAINING_SEKUNDEN = 1;
    public static final int MAX_TRAINING_SEKUNDEN = 86_400;
    public static final int MIN_PAUSE_SEKUNDEN = 0;
    public static final int MAX_PAUSE_SEKUNDEN = 86_400;
    public static final IntervallEinstellung STANDARD = new IntervallEinstellung(30, 10);

    public IntervallEinstellung {
        if (trainingSekunden < MIN_TRAINING_SEKUNDEN || trainingSekunden > MAX_TRAINING_SEKUNDEN) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Trainingsdauer muss zwischen " + MIN_TRAINING_SEKUNDEN + " und "
                            + MAX_TRAINING_SEKUNDEN + " Sekunden liegen."
            );
        }
        if (pauseSekunden < MIN_PAUSE_SEKUNDEN || pauseSekunden > MAX_PAUSE_SEKUNDEN) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Pausendauer muss zwischen " + MIN_PAUSE_SEKUNDEN + " und "
                            + MAX_PAUSE_SEKUNDEN + " Sekunden liegen."
            );
        }
    }
}
