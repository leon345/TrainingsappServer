package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ein registrierter Passkey (WebAuthn-Credential) eines Benutzers.
 * Haelt genau die Daten, die fuer eine spaetere Anmeldung noetig sind
 * (vgl. {@code WebAuthnCredentialRecord.RequiredPersistedData}).
 *
 * @param publicKey   X.509-kodierter oeffentlicher Schluessel
 * @param algorithmus COSE-Algorithmus (z. B. -7 = ES256)
 * @param zaehler     Signaturzaehler des Authenticators
 */
public record Passkey(PasskeyId id, BenutzerId benutzerId, UUID aaguid, byte[] publicKey,
                      long algorithmus, long zaehler, Instant erstelltAm) {

    public Passkey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(benutzerId, "benutzerId");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(erstelltAm, "erstelltAm");
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    public Passkey mitZaehler(long neuerZaehler) {
        return new Passkey(id, benutzerId, aaguid, publicKey, algorithmus, neuerZaehler, erstelltAm);
    }
}
