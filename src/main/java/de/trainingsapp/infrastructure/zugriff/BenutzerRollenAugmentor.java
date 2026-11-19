package de.trainingsapp.infrastructure.zugriff;

import de.trainingsapp.infrastructure.passkey.QuarkusWebAuthnUserProvider;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Lädt fachliche Benutzerrollen über Quarkus SecurityIdentityAugmentor auf einem Worker-Thread
 * (blockierungsfrei gegenüber der Vert.x Event-Loop und unabhängig vom konkreten Auth-Mechanismus).
 */
@ApplicationScoped
public class BenutzerRollenAugmentor implements SecurityIdentityAugmentor {

    private final QuarkusWebAuthnUserProvider userProvider;

    public BenutzerRollenAugmentor(QuarkusWebAuthnUserProvider userProvider) {
        this.userProvider = userProvider;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> {
            Set<String> rollen = userProvider.ladeRollenFuerPrincipal(identity.getPrincipal().getName());
            if (rollen.isEmpty()) {
                return identity;
            }
            return QuarkusSecurityIdentity.builder(identity)
                    .addRoles(rollen)
                    .build();
        });
    }
}
