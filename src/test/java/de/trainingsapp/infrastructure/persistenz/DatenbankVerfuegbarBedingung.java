package de.trainingsapp.infrastructure.persistenz;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Datenbank-Tests sind Pflicht (E19): Ohne Datenbank bzw. ohne Docker schlagen sie fehl.
 *
 * <p>Nur mit {@code -Ddatenbank.tests=aus} werden sie ausdruecklich uebersprungen;
 * dann erscheint eine WARN-Meldung im Log und die Tests als "skipped".
 * Eine CI darf {@code aus} nie setzen. Andere Werte sind ein Fehler.
 */
public class DatenbankVerfuegbarBedingung implements ExecutionCondition {

    static final String SCHALTER = "datenbank.tests";

    private static final Logger LOG = Logger.getLogger(DatenbankVerfuegbarBedingung.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String wert = System.getProperty(SCHALTER);
        if (wert == null) {
            return ConditionEvaluationResult.enabled("Datenbank-Tests sind Pflicht");
        }
        if ("aus".equals(wert)) {
            String test = context.getDisplayName();
            LOG.warnf("Datenbank-Tests AUSGESCHALTET (-D%s=aus): %s wird uebersprungen. "
                    + "Nur lokal zulaessig, nie in CI.", SCHALTER, test);
            return ConditionEvaluationResult.disabled(SCHALTER + "=aus: Datenbank-Test uebersprungen");
        }
        throw new IllegalArgumentException("Unbekannter Wert -D" + SCHALTER + "=" + wert
                + "; erlaubt ist nur 'aus' (ohne Schalter sind Datenbank-Tests Pflicht)");
    }
}
