package de.trainingsapp.e2e;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * E2E-Tests sind Pflicht (wie Datenbank-Tests nach E19). Ohne Browser schlagen sie fehl; Playwright laedt
 * die passende Chromium-Revision beim ersten Lauf in den Benutzer-Cache (~/.cache/ms-playwright).
 * Nur mit {@code -De2e.tests=aus} werden sie uebersprungen, mit WARN und als "skipped". Andere Werte sind ein Fehler.
 */
public class E2eSchalterBedingung implements ExecutionCondition {

    static final String SCHALTER = "e2e.tests";
    private static final Logger LOG = Logger.getLogger(E2eSchalterBedingung.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String wert = System.getProperty(SCHALTER);
        if (wert == null) {
            return ConditionEvaluationResult.enabled("E2E-Tests sind Pflicht");
        }
        if ("aus".equals(wert)) {
            LOG.warnf("E2E-Tests AUSGESCHALTET (-D%s=aus): %s wird uebersprungen. Nur lokal zulaessig, nie in CI.",
                    SCHALTER, context.getDisplayName());
            return ConditionEvaluationResult.disabled(SCHALTER + "=aus: E2E-Test uebersprungen");
        }
        throw new IllegalArgumentException("Unbekannter Wert -D" + SCHALTER + "=" + wert
                + "; erlaubt ist nur 'aus' (ohne Schalter sind E2E-Tests Pflicht)");
    }
}
