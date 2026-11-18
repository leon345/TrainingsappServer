package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnUserProvider;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;
import java.util.List;

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

    static WebAuthnCredentialRecord zuRecord(Passkey p, String username) {
        return WebAuthnCredentialRecord.fromRequiredPersistedData(
                new WebAuthnCredentialRecord.RequiredPersistedData(username, p.id().wert(), p.aaguid(),
                        p.publicKey(), p.algorithmus(), p.zaehler()));
    }
}
