package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.webauthn.WebAuthnSecurity;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import java.util.Optional;

/**
 * Web-Implementierung des dünnen Auth-Ports Anmeldesitzung auf Basis von quarkus-security-webauthn.
 */
@RequestScoped
public class QuarkusAnmeldesitzung implements Anmeldesitzung {

    private final SecurityIdentity securityIdentity;
    private final WebAuthnSecurity webAuthnSecurity;
    private final RoutingContext routingContext;

    public QuarkusAnmeldesitzung(
            SecurityIdentity securityIdentity,
            WebAuthnSecurity webAuthnSecurity,
            RoutingContext routingContext
    ) {
        this.securityIdentity = securityIdentity;
        this.webAuthnSecurity = webAuthnSecurity;
        this.routingContext = routingContext;
    }

    @Override
    public Optional<BenutzerId> aktuellerBenutzer() {
        if (securityIdentity == null || securityIdentity.isAnonymous() || securityIdentity.getPrincipal() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(BenutzerId.ausText(securityIdentity.getPrincipal().getName()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void anmelden(BenutzerId benutzerId) {
        webAuthnSecurity.rememberUser(benutzerId.alsText(), routingContext);
    }

    @Override
    public void beenden() {
        webAuthnSecurity.logout(routingContext);
    }
}
