package de.trainingsapp.domain.zugriff;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.passkey.Passkey;

/**
 * Zentrale, framework-freie RBAC-/ABAC-Zugriffslogik der Domain-Schicht.
 * ADMIN dient ausschließlich Betriebsaufgaben und erhält keinen Zugriff auf fremde Nutzerdaten.
 */
public class ZugriffsPolicy {

    /**
     * Nur der Besitzer darf einen Passkey umbenennen oder löschen (auch ADMIN hat keinen Fremdzugriff).
     */
    public boolean darfPasskeyVerwalten(BenutzerId akteur, Passkey passkey) {
        if (akteur == null || passkey == null) {
            return false;
        }
        return akteur.equals(passkey.benutzerId());
    }

    /**
     * Trainingsdaten (Workouts/Timer) dürfen vom Besitzer, bei expliziter Einladung oder (als angemeldete Rolle USER)
     * im Status öffentlich gelesen werden. ADMIN hat keine Sonderrechte auf fremde Trainingsdaten.
     */
    public boolean darfTrainingsdatenLesen(
            BenutzerId akteur,
            Rolle rolle,
            BenutzerId besitzer,
            boolean oeffentlich,
            boolean eingeladen
    ) {
        if (akteur == null || rolle == null || besitzer == null) {
            return false;
        }
        if (akteur.equals(besitzer)) {
            return true;
        }
        if (rolle == Rolle.ADMIN) {
            return false;
        }
        return oeffentlich || eingeladen;
    }

    /**
     * Trainingsdaten dürfen ausschließlich vom Besitzer bearbeitet werden.
     */
    public boolean darfTrainingsdatenBearbeiten(BenutzerId akteur, BenutzerId besitzer) {
        if (akteur == null || besitzer == null) {
            return false;
        }
        return akteur.equals(besitzer);
    }
}
