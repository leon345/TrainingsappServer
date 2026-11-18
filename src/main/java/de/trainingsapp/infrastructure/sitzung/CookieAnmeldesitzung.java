package de.trainingsapp.infrastructure.sitzung;

import de.trainingsapp.application.sitzung.Anmeldesitzung;
import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.webauthn.WebAuthnSecurity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Cookie-Sitzung der Quarkus-WebAuthn-Extension hinter dem Port {@link Anmeldesitzung} (E15, E23).
 * Die Sitzung liegt verschluesselt im Cookie {@code quarkus.webauthn.cookie-name} (Principal = BenutzerId);
 * ihre Attribute (HttpOnly, Secure, SameSite) setzt {@code infrastructure.http.CookieHaertung}.
 *
 * <p>Bekannte Grenze: zustandslos, daher kein serverseitiger Widerruf vor Ablauf; {@link #beenden()}
 * loescht nur das Cookie im Browser.
 */
@ApplicationScoped
public class CookieAnmeldesitzung implements Anmeldesitzung {

    private final WebAuthnSecurity security;
    private final CurrentVertxRequest request;
    private final SecurityIdentity identity;

    @Inject
    CookieAnmeldesitzung(WebAuthnSecurity security, CurrentVertxRequest request, SecurityIdentity identity) {
        this.security = security;
        this.request = request;
        this.identity = identity;
    }

    @Override
    public Optional<BenutzerId> aktuellerBenutzer() {
        if (identity.isAnonymous()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BenutzerId.aus(identity.getPrincipal().getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void anmelden(BenutzerId benutzer) {
        security.rememberUser(benutzer.toString(), request.getCurrent());
    }

    @Override
    public void beenden() {
        security.logout(request.getCurrent());
    }
}
