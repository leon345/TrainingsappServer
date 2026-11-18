package de.trainingsapp.infrastructure.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * E16, erste Schicht: Im Prod-Profil antwortet der Hauptport fuer alle {@code /q/*} und
 * {@code /.well-known/webauthn} mit 404, ohne Body und ohne {@code Set-Cookie}. Der Filter hat die
 * hoechste eigene Prioritaet und laeuft vor Authentifizierung und Permission-Check von Quarkus
 * (Prioritaet 200 bzw. 100) und vor den Routen der WebAuthn-Extension; kein Handler dahinter wird
 * erreicht. Zweite Schicht ist die Deny-Policy {@code q-sperre} in application.properties, dritte
 * die Proxy-Regel (S-14).
 *
 * <p>Gilt nur fuer den Router des Hauptports. Ein Management-Interface (E12) hat einen eigenen Router.
 */
@ApplicationScoped
public class QPfadSperre {

    private final boolean aktiv;

    QPfadSperre(@ConfigProperty(name = "trainingsapp.q-sperre", defaultValue = "false") boolean aktiv) {
        this.aktiv = aktiv;
    }

    void registriere(@Observes Filters filters) {
        if (!aktiv) {
            return;
        }
        filters.register(rc -> {
            if (gesperrt(rc)) {
                rc.response().setStatusCode(404).end();
            } else {
                rc.next();
            }
        }, FilterPrioritaeten.Q_SPERRE);
    }

    static boolean gesperrt(RoutingContext rc) {
        String pfad = rc.normalizedPath();
        return pfad.equals("/q") || pfad.startsWith("/q/")
                || pfad.equals("/.well-known/webauthn") || pfad.startsWith("/.well-known/webauthn/");
    }
}
