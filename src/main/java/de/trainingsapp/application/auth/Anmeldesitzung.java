package de.trainingsapp.application.auth;

import de.trainingsapp.domain.benutzer.BenutzerId;
import java.util.Optional;

/**
 * Dünner Auth-Port für die aktuelle Anmeldesitzung (Nutzerentscheid A).
 * Kapselt den technischen Sitzungsmechanismus (aktuell WebAuthn-Cookie, künftig z. B. Bearer-/Refresh-Token für native Apps).
 */
public interface Anmeldesitzung {

    Optional<BenutzerId> aktuellerBenutzer();

    void anmelden(BenutzerId benutzerId);

    void beenden();
}
