package de.trainingsapp.infrastructure.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.impl.ServerCookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * E14: setzt {@code HttpOnly} und {@code SameSite=Strict} (und in Prod {@code Secure}) auf das
 * Sitzungs-Cookie {@code quarkus-credential} und das Challenge-Cookie {@code _quarkus_webauthn_challenge}.
 *
 * <p>Die Extension (3.40) uebergibt {@code httpOnly=false} fest an den {@code PersistentLoginManager}
 * und ist darin nicht konfigurierbar. Der Filter aendert deshalb kurz vor dem Schreiben der Header
 * die Cookie-Objekte selbst, nur solche, die in dieser Antwort gesetzt oder geloescht werden.
 * Vert.x schreibt die {@code Set-Cookie}-Header erst nach den Headers-End-Handlern; so entsteht genau
 * ein {@code Set-Cookie} je Cookie. {@code Secure} setzt die Extension selbst, wenn der Request (auch
 * ueber {@code X-Forwarded-Proto} hinter dem Proxy) per HTTPS kam; {@code trainingsapp.cookie.secure}
 * erzwingt es zusaetzlich (Prod).
 *
 * <p>Nutzt {@code io.vertx.core.http.impl.ServerCookie} (interne Vert.x-API, nur hier). Bei einem
 * Vert.x-Update pruefen; der Pflichttest {@code CookieHaertungTest} faellt sonst auf.
 */
@ApplicationScoped
public class CookieHaertung {

    private final List<String> namen;
    private final boolean secureErzwingen;

    CookieHaertung(@ConfigProperty(name = "quarkus.webauthn.cookie-name", defaultValue = "quarkus-credential")
                   String sitzungsCookie,
                   @ConfigProperty(name = "quarkus.webauthn.challenge-cookie-name",
                           defaultValue = "_quarkus_webauthn_challenge") String challengeCookie,
                   @ConfigProperty(name = "trainingsapp.cookie.secure", defaultValue = "false") boolean secureErzwingen) {
        this.namen = List.of(sitzungsCookie, challengeCookie);
        this.secureErzwingen = secureErzwingen;
    }

    void registriere(@Observes Filters filters) {
        filters.register(rc -> {
            rc.addHeadersEndHandler(v -> haerte(rc));
            rc.next();
        }, FilterPrioritaeten.COOKIE_HAERTUNG);
    }

    private void haerte(RoutingContext rc) {
        for (String name : namen) {
            for (Cookie cookie : rc.request().cookies(name)) {
                if (cookie instanceof ServerCookie server && server.isChanged()) {
                    server.setHttpOnly(true);
                    server.setSameSite(CookieSameSite.STRICT);
                    if (secureErzwingen) {
                        server.setSecure(true);
                    }
                }
            }
        }
    }
}
