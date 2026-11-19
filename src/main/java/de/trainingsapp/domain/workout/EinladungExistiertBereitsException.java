package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainKonfliktException;

/**
 * Ausnahme für doppelte Einladung desselben Benutzers zu demselben Workout.
 */
public class EinladungExistiertBereitsException extends DomainKonfliktException {

    public static final String CODE = "EINLADUNG_EXISTIERT_BEREITS";

    public EinladungExistiertBereitsException() {
        super(CODE, "Dieser Benutzer wurde bereits zu diesem Workout eingeladen.");
    }
}
