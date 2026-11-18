package de.trainingsapp.infrastructure.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * E17: Rate-Limit je Client-IP auf den unauthentifizierten Einstiegen {@code POST /v1/registrieren/optionen},
 * {@code POST /v1/registrieren} und {@code POST /v1/anmelden/optionen}; je Endpunkt ein eigenes Kontingent.
 * Festes Zeitfenster, Zaehler im Speicher der Instanz (Voraussetzung: genau eine Instanz).
 *
 * <p>Laeuft als Vert.x-Filter vor Authentifizierung, WebAuthn-Pruefung und jeder Transaktion (E26).
 * Bei Ueberschreitung 429 im Format {@code FehlerV1} mit {@code Retry-After} in Sekunden.
 *
 * <p>Client-IP: {@code remoteAddress()} des Requests. Hinter dem Proxy setzt Quarkus sie aus dem ersten
 * Eintrag von {@code X-Forwarded-For}, aber nur, wenn die Verbindung von einer Adresse aus
 * {@code quarkus.http.proxy.trusted-proxies} kommt. Voraussetzung an den Proxy (S-14): Er muss
 * {@code X-Forwarded-For} mit der echten Client-Adresse ueberschreiben, nicht anhaengen, und die
 * Anwendung darf nur ueber ihn erreichbar sein.
 */
@ApplicationScoped
public class RateLimit {

    static final String KOERPER = "{\"code\":\"ZU_VIELE_ANFRAGEN\",\"meldung\":\"Zu viele Anfragen, bitte spaeter erneut versuchen\"}";
    static final Set<String> PFADE = Set.of("/v1/registrieren/optionen", "/v1/registrieren", "/v1/anmelden/optionen");
    private static final int AUFRAEUMEN_AB = 10_000;

    private record Fenster(Instant beginn, int anzahl) {
    }

    private final Map<String, Fenster> zaehler = new ConcurrentHashMap<>();
    private final Clock uhr;
    private final int grenze;
    private final Duration fenster;

    RateLimit(Clock uhr,
              @ConfigProperty(name = "trainingsapp.rate-limit.anfragen") int grenze,
              @ConfigProperty(name = "trainingsapp.rate-limit.fenster") Duration fenster) {
        if (grenze < 1 || fenster.isNegative() || fenster.isZero()) {
            throw new IllegalStateException("trainingsapp.rate-limit.* muss positiv sein");
        }
        this.uhr = uhr;
        this.grenze = grenze;
        this.fenster = fenster;
    }

    void registriere(@Observes Filters filters) {
        filters.register(this::pruefe, FilterPrioritaeten.RATE_LIMIT);
    }

    void pruefe(RoutingContext rc) {
        String pfad = rc.normalizedPath();
        if (rc.request().method() != HttpMethod.POST || !PFADE.contains(pfad)) {
            rc.next();
            return;
        }
        Instant jetzt = uhr.instant();
        aufraeumen(jetzt);
        Fenster f = zaehler.compute(clientIp(rc) + " " + pfad, (k, alt) ->
                alt == null || !jetzt.isBefore(alt.beginn().plus(fenster))
                        ? new Fenster(jetzt, 1)
                        : new Fenster(alt.beginn(), alt.anzahl() + 1));
        if (f.anzahl() > grenze) {
            long rest = Duration.between(jetzt, f.beginn().plus(fenster)).toMillis();
            long sekunden = Math.max(1, (rest + 999) / 1000);
            rc.response().setStatusCode(429)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.RETRY_AFTER, Long.toString(sekunden))
                    .end(KOERPER);
            return;
        }
        rc.next();
    }

    private static String clientIp(RoutingContext rc) {
        SocketAddress adresse = rc.request().remoteAddress();
        return adresse == null ? "unbekannt" : adresse.hostAddress() != null ? adresse.hostAddress() : adresse.host();
    }

    private void aufraeumen(Instant jetzt) {
        if (zaehler.size() > AUFRAEUMEN_AB) {
            zaehler.values().removeIf(f -> !jetzt.isBefore(f.beginn().plus(fenster)));
        }
    }
}
