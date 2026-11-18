package de.trainingsapp.e2e;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Markiert Browser-E2E-Tests (Chromium ueber Playwright). Muster wie E19: Standard ist Pflicht;
 * nur {@code -De2e.tests=aus} ueberspringt sie, mit WARN im Log.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(E2eSchalterBedingung.class)
public @interface E2eErforderlich {
}
