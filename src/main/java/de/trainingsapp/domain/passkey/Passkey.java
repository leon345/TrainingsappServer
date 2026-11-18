package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein registrierter Passkey (WebAuthn-Credential) eines Benutzers. Haelt die Daten, die fuer eine
 * spaetere Anmeldung noetig sind, und die Angaben fuer die Verwaltung (E29). Bewusst ohne
 * Geraete- oder Attestierungsdaten (kein AAGUID), ohne IP und ohne User-Agent.
 *
 * @param publicKey          X.509-kodierter oeffentlicher Schluessel
 * @param algorithmus        COSE-Algorithmus (z. B. -7 = ES256)
 * @param zaehler            Signaturzaehler des Authenticators
 * @param erstelltAm         Tag der Registrierung (UTC)
 * @param zuletztVerwendetAm Tag der letzten Anmeldung (UTC), {@code null} wenn nie verwendet
 */
public record Passkey(PasskeyId id, BenutzerId benutzerId, PasskeyBezeichnung bezeichnung, byte[] publicKey,
                      long algorithmus, long zaehler, LocalDate erstelltAm, LocalDate zuletztVerwendetAm) {

    public Passkey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(benutzerId, "benutzerId");
        Objects.requireNonNull(bezeichnung, "bezeichnung");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(erstelltAm, "erstelltAm");
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    public Optional<LocalDate> zuletztVerwendet() {
        return Optional.ofNullable(zuletztVerwendetAm);
    }

    public Passkey mitZaehler(long neuerZaehler) {
        return new Passkey(id, benutzerId, bezeichnung, publicKey, algorithmus, neuerZaehler, erstelltAm,
                zuletztVerwendetAm);
    }
}
