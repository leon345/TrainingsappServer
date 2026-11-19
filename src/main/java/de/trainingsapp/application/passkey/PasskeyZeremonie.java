package de.trainingsapp.application.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application-Port für WebAuthn-Zeremonien (Challenge-Erzeugung und kryptografische Verifikation).
 * Hält die Application-Schicht frei von io.quarkus.security.webauthn und Vert.x.
 */
public interface PasskeyZeremonie {

    String erstelleRegistrierungsOptionen(
            BenutzerId benutzerId,
            Benutzername benutzername,
            List<String> ausgeschlosseneCredentialIds
    );

    VerifizierterNeuerPasskey verifiziereRegistrierung(
            BenutzerId benutzerId,
            RegistrierungsAntwort antwort
    );

    String erstelleAnmeldeOptionen(List<String> erlaubteCredentialIds);

    VerifizierteAnmeldung verifiziereAnmeldung(AnmeldeAntwort antwort);

    record RegistrierungsAntwort(
            String id,
            String rawId,
            String type,
            String clientDataJson,
            String attestationObject
    ) {
    }

    record AnmeldeAntwort(
            String id,
            String rawId,
            String type,
            String clientDataJson,
            String authenticatorData,
            String signature,
            String userHandle
    ) {
    }

    record VerifizierterNeuerPasskey(
            String credentialId,
            byte[] publicKey,
            long publicKeyAlgorithm,
            long signCount,
            UUID aaguid
    ) {
        public VerifizierterNeuerPasskey {
            Objects.requireNonNull(credentialId, "credentialId darf nicht null sein");
            Objects.requireNonNull(publicKey, "publicKey darf nicht null sein");
            publicKey = Arrays.copyOf(publicKey, publicKey.length);
            Objects.requireNonNull(aaguid, "aaguid darf nicht null sein");
        }

        @Override
        public byte[] publicKey() {
            return Arrays.copyOf(publicKey, publicKey.length);
        }
    }

    record VerifizierteAnmeldung(
            BenutzerId benutzerId,
            String credentialId,
            long neuerSignCount
    ) {
        public VerifizierteAnmeldung {
            Objects.requireNonNull(benutzerId, "benutzerId darf nicht null sein");
            Objects.requireNonNull(credentialId, "credentialId darf nicht null sein");
        }
    }
}
