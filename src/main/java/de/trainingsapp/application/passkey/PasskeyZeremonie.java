package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;

import java.util.List;

/**
 * Port zur WebAuthn-Bibliothek (E3: "hinter eigenem Port-Interface").
 *
 * <p>Kapselt Challenge-Erzeugung und kryptografische Pruefung der Antworten
 * des Authenticators. Wie die Challenge zwischen Optionen-Abruf und Abschluss
 * aufbewahrt wird (Cookie, Server-Zustand), ist Sache des Adapters.
 * Optionen und Antworten werden als JSON-Text durchgereicht
 * (PublicKeyCredentialCreationOptions/RequestOptions bzw. PublicKeyCredential).
 */
public interface PasskeyZeremonie {

    /**
     * Erzeugt Registrierungsoptionen fuer {@code benutzer}. Der User-Handle muss
     * stabil aus der {@code BenutzerId} abgeleitet werden; bereits registrierte
     * Passkeys werden als {@code excludeCredentials} uebergeben.
     */
    String registrierungsOptionen(Benutzer benutzer, List<PasskeyId> vorhandene);

    /**
     * Prueft die Registrierungsantwort gegen die zuvor ausgegebene Challenge.
     *
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort
     */
    NeuerPasskey registrierungPruefen(String antwortJson);

    /** Erzeugt Anmeldeoptionen ohne Benutzernamen (discoverable credentials). */
    String anmeldeOptionen();

    /**
     * Prueft die Anmeldeantwort; der gespeicherte Passkey wird ueber den Port
     * {@code PasskeyRepository} gefunden.
     *
     * @return der verwendete Passkey mit dem neuen Signaturzaehler
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort oder unbekanntem Passkey
     */
    Passkey anmeldungPruefen(String antwortJson);

    /** Daten eines neu registrierten, noch keinem Benutzer zugeordneten Passkeys. */
    record NeuerPasskey(PasskeyId id, byte[] publicKey, long algorithmus, long zaehler) {
    }
}
