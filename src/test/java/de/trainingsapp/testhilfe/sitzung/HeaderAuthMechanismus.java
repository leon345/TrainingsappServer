package de.trainingsapp.testhilfe.sitzung;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Set;

/**
 * NUR TESTQUELLEN (E23, Abnahme 6): Quarkus-Security-Mechanismus fuer die Header-Sitzung, damit
 * {@code @Authenticated} und die Pfad-Policy mit ihr unveraendert funktionieren.
 */
@Alternative
@ApplicationScoped
public class HeaderAuthMechanismus implements HttpAuthenticationMechanism {

    private final HeaderSitzungen sitzungen;

    HeaderAuthMechanismus(HeaderSitzungen sitzungen) {
        this.sitzungen = sitzungen;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        return sitzungen.pruefe(context.request().getHeader(HeaderSitzungen.HEADER))
                .map(benutzer -> Uni.createFrom().<SecurityIdentity>item(QuarkusSecurityIdentity.builder()
                        .setPrincipal(new QuarkusPrincipal(benutzer.toString())).build()))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, null, null));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 3000;
    }
}
