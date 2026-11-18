package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

/** Port: Sitzung nach erfolgreicher Passkey-Pruefung herstellen bzw. beenden. */
public interface Anmeldesitzung {

    void starte(BenutzerId benutzerId);

    void beende();
}
