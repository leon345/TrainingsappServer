package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.BenutzerRepository;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnUserProvider;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;

/**
 * Brücke zwischen quarkus-security-webauthn und den Domain-Repositories.
 * Der Principal in Quarkus Security ist die unveränderliche BenutzerId (UUID als Text).
 * Rollen werden blockierungsfrei über BenutzerRollenAugmentor (SecurityIdentityAugmentor) nachgeladen.
 */
@ApplicationScoped
public class QuarkusWebAuthnUserProvider implements WebAuthnUserProvider {

    private final BenutzerRepository benutzerRepository;
    private final PasskeyRepository passkeyRepository;

    public QuarkusWebAuthnUserProvider(
            BenutzerRepository benutzerRepository,
            PasskeyRepository passkeyRepository
    ) {
        this.benutzerRepository = benutzerRepository;
        this.passkeyRepository = passkeyRepository;
    }

    @Override
    @Transactional
    public Uni<List<WebAuthnCredentialRecord>> findByUsername(String benutzerIdText) {
        BenutzerId benutzerId = parseBenutzerIdOderNull(benutzerIdText);
        if (benutzerId == null) {
            return Uni.createFrom().item(List.of());
        }
        List<WebAuthnCredentialRecord> records = passkeyRepository.findeAlleNachBenutzerId(benutzerId)
                .stream()
                .map(QuarkusWebAuthnUserProvider::zuCredentialRecord)
                .toList();
        return Uni.createFrom().item(records);
    }

    @Override
    @Transactional
    public Uni<WebAuthnCredentialRecord> findByCredentialId(String credentialId) {
        WebAuthnCredentialRecord record = passkeyRepository.findeNachCredentialId(credentialId)
                .map(QuarkusWebAuthnUserProvider::zuCredentialRecord)
                .orElse(null);
        return Uni.createFrom().item(record);
    }

    @ActivateRequestContext
    @Transactional
    public Set<String> ladeRollenFuerPrincipal(String benutzerIdText) {
        BenutzerId benutzerId = parseBenutzerIdOderNull(benutzerIdText);
        if (benutzerId == null) {
            return Set.of();
        }
        return benutzerRepository.findeNachId(benutzerId)
                .map(benutzer -> Set.of(benutzer.rolle().name()))
                .orElseGet(Set::of);
    }

    public static WebAuthnCredentialRecord zuCredentialRecord(Passkey passkey) {
        WebAuthnCredentialRecord.RequiredPersistedData persisted =
                new WebAuthnCredentialRecord.RequiredPersistedData(
                        passkey.benutzerId().alsText(),
                        passkey.credentialId(),
                        passkey.aaguid(),
                        passkey.publicKey(),
                        passkey.publicKeyAlgorithm(),
                        passkey.signCount()
                );
        return WebAuthnCredentialRecord.fromRequiredPersistedData(persisted);
    }

    private BenutzerId parseBenutzerIdOderNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return BenutzerId.ausText(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
