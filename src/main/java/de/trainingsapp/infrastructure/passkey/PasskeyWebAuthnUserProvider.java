package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EdDSACOSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnUserProvider;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Bruecke von der Quarkus-WebAuthn-Extension zu unseren Ports. Die Extension liest
 * hierueber gespeicherte Credentials (Anmeldung). Gespeichert wird NICHT hier
 * ({@code store} bleibt Default/no-op), sondern im {@code PasskeyService}, weil die
 * eingebauten Endpunkte /q/webauthn/register und /login abgeschaltet bleiben.
 *
 * <p>Der "username" der Extension ist bei uns der Benutzername (wird dem Authenticator
 * angezeigt). Die Sitzung traegt dagegen die {@code BenutzerId}.
 */
@ApplicationScoped
@Blocking
public class PasskeyWebAuthnUserProvider implements WebAuthnUserProvider {

    private final PasskeyRepository passkeys;

    PasskeyWebAuthnUserProvider(PasskeyRepository passkeys) {
        this.passkeys = passkeys;
    }

    /**
     * Liefert bewusst immer eine leere Liste, ohne Datenbankzugriff. Die Extension (3.40) nutzt das
     * Ergebnis nur fuer excludeCredentials bzw. allowCredentials, und beide Stellen sind dort
     * abgeschaltet (quarkus#44292). excludeCredentials setzt {@link QuarkusWebAuthnZeremonie} selbst.
     * So loest auch der eingebaute Endpunkt /q/webauthn/register-options-challenge keine Abfrage aus.
     */
    @Override
    public Uni<List<WebAuthnCredentialRecord>> findByUsername(String username) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<WebAuthnCredentialRecord> findByCredentialId(String credentialId) {
        return passkeys.findeNachId(normalisiere(credentialId))
                .map(p -> Uni.createFrom().item(zuRecord(p, p.benutzerId().toString())))
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Unbekannter Passkey")));
    }

    static PasskeyId normalisiere(String credentialIdBase64Url) {
        // Browser senden Base64URL ohne Padding; zur Sicherheit vereinheitlichen.
        byte[] roh = Base64.getUrlDecoder().decode(credentialIdBase64Url);
        return new PasskeyId(Base64.getUrlEncoder().withoutPadding().encodeToString(roh));
    }

    /** AAGUID wird nicht gespeichert (E29: keine Geraetedaten); fuer die Anmeldepruefung ist er ohne Bedeutung. */
    private static final UUID OHNE_AAGUID = new UUID(0, 0);

    static WebAuthnCredentialRecord zuRecord(Passkey p, String username) {
        if (p.algorithmus() == COSEAlgorithmIdentifier.EdDSA.getValue()) {
            return eddsaRecord(p, username);
        }
        return WebAuthnCredentialRecord.fromRequiredPersistedData(
                new WebAuthnCredentialRecord.RequiredPersistedData(username, p.id().wert(), OHNE_AAGUID,
                        p.publicKey(), p.algorithmus(), p.zaehler()));
    }

    /**
     * Umgehung fuer EdDSA (Befund, siehe {@code PasskeyRecordAlgorithmenTest}): In Quarkus 3.40.1.2 erzeugt
     * {@code fromRequiredPersistedData} den Algorithmus mit {@code COSEAlgorithmIdentifier.create(-8)}, also
     * als neue Instanz; webauthn4j 0.30.3 prueft in {@code EdDSACOSEKey.create} aber auf Identitaet mit der
     * Konstante und lehnt ab. Ohne Umgehung ist ein EdDSA-Passkey registrierbar, aber nie zur Anmeldung nutzbar.
     * Der Datensatz wird deshalb ueber den oeffentlichen Konstruktor aus einem minimalen Attestation-Objekt
     * (Attestation "none") gebaut. Bei der Anmeldung nutzt webauthn4j daraus nur Schluessel, Credential-ID
     * und Zaehler; rpIdHash und Flags dieses Hilfsobjekts werden nicht geprueft.
     */
    private static WebAuthnCredentialRecord eddsaRecord(Passkey p, String username) {
        EdECPublicKey schluessel;
        try {
            schluessel = (EdECPublicKey) KeyFactory.getInstance("EdDSA")
                    .generatePublic(new X509EncodedKeySpec(p.publicKey()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Ungueltiger EdDSA-Schluessel", e);
        }
        AttestedCredentialData daten = new AttestedCredentialData(AAGUID.ZERO,
                Base64.getUrlDecoder().decode(p.id().wert()),
                EdDSACOSEKey.create(schluessel, COSEAlgorithmIdentifier.EdDSA));
        AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authData = new AuthenticatorData<>(
                new byte[32], (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_AT), p.zaehler(), daten);
        return new WebAuthnCredentialRecord(username, new AttestationObject(authData, new NoneAttestationStatement()),
                null, null, null);
    }
}
