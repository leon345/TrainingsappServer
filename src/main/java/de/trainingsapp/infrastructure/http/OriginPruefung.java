package de.trainingsapp.infrastructure.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * E15: Schutz gegen CSRF neben {@code SameSite=Strict}. Jeder schreibende Request (POST, PUT, PATCH,
 * DELETE) unter {@code /v1} muss einen {@code Origin}-Header tragen, der exakt in der Allowlist
 * {@code quarkus.webauthn.origins} steht. Sonst 403 mit {@code FehlerV1} und Code
 * {@code ORIGIN_UNGUELTIG}, bevor Authentifizierung oder Endpunkt laufen.
 *
 * <p>Annahme (im Beschluss offen): Fehlt der Header, wird ebenfalls abgewiesen. Browser senden
 * {@code Origin} bei allen POST/PUT/PATCH/DELETE per fetch mit. Clients ohne Browser (CLI, spaetere
 * native App mit Token-Sitzung) muessen den Header setzen oder bekommen eine eigene Regel, sobald es
 * sie gibt (C-15); siehe docs/auth-erweiterung.md.
 */
@ApplicationScoped
public class OriginPruefung {

    static final String KOERPER =
            "{\"code\":\"ORIGIN_UNGUELTIG\",\"meldung\":\"Anfrage von unzulaessiger oder fehlender Origin\"}";

    private static final Set<HttpMethod> SCHREIBEND =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final Set<String> erlaubt;

    OriginPruefung(@ConfigProperty(name = "quarkus.webauthn.origins") List<String> origins) {
        this.erlaubt = Set.copyOf(origins);
    }

    void registriere(@Observes Filters filters) {
        filters.register(this::pruefe, FilterPrioritaeten.ORIGIN_PRUEFUNG);
    }

    void pruefe(RoutingContext rc) {
        String origin = rc.request().getHeader(HttpHeaders.ORIGIN);
        if (betroffen(rc) && (origin == null || !erlaubt.contains(origin))) {
            rc.response().setStatusCode(403)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(KOERPER);
            return;
        }
        rc.next();
    }

    private static boolean betroffen(RoutingContext rc) {
        String pfad = rc.normalizedPath();
        return SCHREIBEND.contains(rc.request().method()) && (pfad.equals("/v1") || pfad.startsWith("/v1/"));
    }
}
