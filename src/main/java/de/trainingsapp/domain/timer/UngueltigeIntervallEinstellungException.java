package de.trainingsapp.domain.timer;

/**
 * Wird geworfen, wenn eine Intervalltimer-Einstellung gegen die Domain-Regeln verstoesst.
 */
public class UngueltigeIntervallEinstellungException extends IllegalArgumentException {

    public UngueltigeIntervallEinstellungException(String message) {
        super(message);
    }
}
