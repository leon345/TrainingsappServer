package de.trainingsapp.infrastructure.passkey;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Origins und rpId nur ueber Konfiguration mit Allowlist (E23, Abnahme 7; E15).
 *
 * <p>Ohne Konfiguration leitet die Extension Origin und rpId aus der Request-URL (Host-Header) ab.
 * Deshalb sind {@code quarkus.webauthn.origins} und {@code quarkus.webauthn.relying-party.id} Pflicht;
 * der Start bricht ab, wenn sie fehlen oder nicht zusammenpassen. Dieselbe Liste nutzt die
 * Origin-Pruefung fuer schreibende Requests.
 */
@ApplicationScoped
public class WebAuthnKonfigurationPruefung {

    void beimStart(@Observes StartupEvent ereignis,
                   @ConfigProperty(name = "quarkus.webauthn.origins") Optional<List<String>> origins,
                   @ConfigProperty(name = "quarkus.webauthn.relying-party.id") Optional<String> rpId) {
        pruefe(origins.orElse(List.of()), rpId.orElse(""));
    }

    /**
     * @throws IllegalStateException wenn die Allowlist leer ist, ein Eintrag kein reiner Origin ist,
     *                               ohne HTTPS auf einen anderen Host als localhost zeigt oder nicht
     *                               zur rpId gehoert
     */
    static void pruefe(List<String> origins, String rpId) {
        if (rpId == null || rpId.isBlank() || !rpId.equals(rpId.toLowerCase(Locale.ROOT)) || rpId.contains(":")
                || rpId.contains("/") || rpId.contains("*")) {
            throw new IllegalStateException("quarkus.webauthn.relying-party.id muss ein Hostname sein: '" + rpId + "'");
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException("quarkus.webauthn.origins (Allowlist) ist leer");
        }
        for (String origin : origins) {
            URI uri;
            try {
                uri = new URI(origin);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Origin ungueltig: '" + origin + "'", e);
            }
            String host = uri.getHost();
            boolean nurOrigin = uri.getScheme() != null && host != null && uri.getUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
            if (!nurOrigin || origin.contains("*")) {
                throw new IllegalStateException("Origin muss Schema://Host[:Port] ohne Pfad sein: '" + origin + "'");
            }
            boolean lokal = "localhost".equals(host) || "127.0.0.1".equals(host);
            if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && lokal)) {
                throw new IllegalStateException("Origin ohne HTTPS ist nur fuer localhost erlaubt: '" + origin + "'");
            }
            if (!host.equals(rpId) && !host.endsWith("." + rpId)) {
                throw new IllegalStateException("Origin '" + origin + "' gehoert nicht zur rpId '" + rpId + "'");
            }
        }
    }
}
