package de.trainingsapp.infrastructure.persistenz;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Markiert Tests, die eine echte PostgreSQL-Datenbank brauchen (zusammen mit
 * {@code @TestProfile(MitDatenbank.class)}). Sie laufen immer, ausser mit
 * {@code -Ddatenbank.tests=aus}, siehe {@link DatenbankVerfuegbarBedingung}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DatenbankVerfuegbarBedingung.class)
public @interface DatenbankErforderlich {
}
