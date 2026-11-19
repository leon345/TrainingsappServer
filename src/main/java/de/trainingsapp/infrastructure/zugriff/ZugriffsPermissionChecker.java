package de.trainingsapp.infrastructure.zugriff;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyNichtGefundenException;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import io.quarkus.security.PermissionChecker;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Quarkus @PermissionChecker-Adapter, der ABAC-Prüfungen an die Domain-ZugriffsPolicy delegiert.
 */
@ApplicationScoped
public class ZugriffsPermissionChecker {

    private final PasskeyRepository passkeyRepository;
    private final ZugriffsPolicy zugriffsPolicy;

    public ZugriffsPermissionChecker(PasskeyRepository passkeyRepository, ZugriffsPolicy zugriffsPolicy) {
        this.passkeyRepository = passkeyRepository;
        this.zugriffsPolicy = zugriffsPolicy;
    }

    @PermissionChecker("passkey:verwalten")
    @Blocking
    @Transactional
    public boolean darfPasskeyVerwalten(UUID id, SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || id == null) {
            return false;
        }
        BenutzerId akteur = BenutzerId.ausText(identity.getPrincipal().getName());
        boolean erlaubt = passkeyRepository.findeNachId(new PasskeyId(id))
                .map(passkey -> zugriffsPolicy.darfPasskeyVerwalten(akteur, passkey))
                .orElse(false);
        if (!erlaubt) {
            // Einheitlich 404 für fehlende und fremde Passkeys (verhindert ID-Aufzählung).
            throw new PasskeyNichtGefundenException();
        }
        return true;
    }
}
