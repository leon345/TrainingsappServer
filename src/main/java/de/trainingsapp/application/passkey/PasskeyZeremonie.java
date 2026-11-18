package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;

import java.util.List;

/**
 * Port zur WebAuthn-Bibliothek (E3: "hinter eigenem Port-Interface").
 *
 * <p>Kapselt Challenge-Erzeugung und kryptografische Pruefung der Antworten des Authenticators,
 * einschliesslich Origin, rpId, User Verification, Signaturzaehler und einmaliger Verwendung der
 * Challenge. Wie die Challenge zwischen Optionen-Abruf und Abschluss aufbewahrt wird (Cookie,
 * Server-Zustand), ist Sache des Adapters. Optionen und Antworten werden als JSON-Text durchgereicht
 * (PublicKeyCredentialCreationOptions/RequestOptions bzw. PublicKeyCredential).
 */
public interface PasskeyZeremonie {

    /**
     * Erzeugt Registrierungsoptionen fuer {@code benutzer} mit einer neuen Challenge. Der User-Handle
     * wird stabil aus der {@code BenutzerId} abgeleitet; bereits registrierte Passkeys werden als
     * {@code excludeCredentials} uebergeben.
     */
    Optionen registrierungsOptionen(Benutzer benutzer, List<PasskeyId> vorhandene);

    /**
     * Prueft die Registrierungsantwort gegen die zuvor ausgegebene Challenge und verbraucht sie.
     *
     * @return der neue Passkey samt der Challenge, auf die er antwortet
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort oder schon verwendeter Challenge
     */
    NeuerPasskey registrierungPruefen(String antwortJson);

    /** Erzeugt Anmeldeoptionen ohne Benutzernamen (discoverable credentials) mit einer neuen Challenge. */
    String anmeldeOptionen();

    /**
     * Prueft die Anmeldeantwort und verbraucht die Challenge. Der gespeicherte Passkey wird ueber den
     * Port {@code PasskeyRepository} gefunden; der User-Handle der Antwort muss zu dessen Besitzer passen.
     *
     * @return der verwendete Passkey mit dem neuen Signaturzaehler
     * @throws PasskeyPruefungFehlgeschlagen bei ungueltiger Antwort, unbekanntem Passkey, fehlendem oder
     *                                       fremdem User-Handle oder schon verwendeter Challenge
     */
    Passkey anmeldungPruefen(String antwortJson);

    /** Optionen als JSON und die darin enthaltene Challenge (Base64URL). */
    record Optionen(String json, String challenge) {
    }

    /** Daten eines neu registrierten, noch keinem Benutzer zugeordneten Passkeys. */
    record NeuerPasskey(PasskeyId id, byte[] publicKey, long algorithmus, long zaehler, String challenge) {
    }
}
