package de.trainingsapp.infrastructure.http;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * 401 als JSON statt 302 (E25, E16). Der WebAuthn-Mechanismus der Extension antwortet auf fehlende
 * Anmeldung mit 302 auf {@code quarkus.webauthn.login-page}. Dieser Mechanismus authentifiziert
 * selbst nie, hat aber eine hoehere Prioritaet und liefert deshalb die Challenge: 401 mit demselben
 * JSON-Format ({@code FehlerV1}) wie der Fehler-Mapper der API.
 */
@ApplicationScoped
public class JsonAnmeldungErforderlich implements HttpAuthenticationMechanism {

    /** Entspricht {@code api.v1.FehlerResponse("NICHT_ANGEMELDET", "Anmeldung erforderlich")}. */
    static final String KOERPER = "{\"code\":\"NICHT_ANGEMELDET\",\"meldung\":\"Anmeldung erforderlich\"}";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, null, null));
    }

    @Override
    public Uni<Boolean> sendChallenge(RoutingContext context) {
        if (!context.response().ended()) {
            context.response().setStatusCode(401)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(KOERPER);
        }
        return Uni.createFrom().item(true);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 2000;
    }
}
