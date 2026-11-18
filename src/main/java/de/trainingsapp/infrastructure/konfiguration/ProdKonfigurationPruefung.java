package de.trainingsapp.infrastructure.konfiguration;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Bricht im Prod-Profil den Start ab, wenn die Sitzungs-Konfiguration fehlt oder unsicher ist (B4).
 *
 * <p>Hintergrund: Fehlt {@code quarkus.http.auth.session.encryption-key}, erzeugt die WebAuthn-Extension
 * stillschweigend einen Zufallsschluessel und meldet nur eine WARN-Zeile (mit dem Schluessel im Log).
 * Nach jedem Neustart waeren alle Sitzungen ungueltig. Ist die Umgebungsvariable gar nicht gesetzt,
 * scheitert der Start schon an der Konfiguration ({@code ${TRAININGSAPP_SITZUNG_SCHLUESSEL}} ohne
 * Standardwert). Ist sie leer gesetzt, behandelt Quarkus den Wert als fehlend; diesen stillen
 * Rueckfall faengt diese Pruefung ab. Ebenso muss {@code Secure} fuer Cookies in Prod an sein (E14).
 */
@ApplicationScoped
public class ProdKonfigurationPruefung {

    static final int MINDESTLAENGE_SCHLUESSEL = 32;

    void beimStart(@Observes StartupEvent ereignis,
                   @ConfigProperty(name = "quarkus.http.auth.session.encryption-key") Optional<String> schluessel,
                   @ConfigProperty(name = "trainingsapp.cookie.secure") boolean cookieSecure) {
        pruefe(ConfigUtils.isProfileActive("prod"), schluessel, cookieSecure);
    }

    static void pruefe(boolean prod, Optional<String> schluessel, boolean cookieSecure) {
        if (!prod) {
            return;
        }
        if (schluessel.filter(s -> !s.isBlank()).isEmpty()) {
            throw new IllegalStateException("TRAININGSAPP_SITZUNG_SCHLUESSEL fehlt oder ist leer; ohne ihn wuerde "
                    + "ein zufaelliger Sitzungsschluessel erzeugt (Sitzungen nach jedem Neustart ungueltig)");
        }
        if (schluessel.get().length() < MINDESTLAENGE_SCHLUESSEL) {
            throw new IllegalStateException("TRAININGSAPP_SITZUNG_SCHLUESSEL muss mindestens "
                    + MINDESTLAENGE_SCHLUESSEL + " Zeichen lang sein");
        }
        if (!cookieSecure) {
            throw new IllegalStateException("TRAININGSAPP_COOKIE_SECURE muss im Prod-Profil true sein (E14)");
        }
    }
}
