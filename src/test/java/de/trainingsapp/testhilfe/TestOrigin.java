package de.trainingsapp.testhilfe;

import java.net.URI;
import java.util.List;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Origin, den ein Browser fuer Seiten dieses Servers sendet: der erste Eintrag der Allowlist
 * {@code quarkus.webauthn.origins} des laufenden Testprofils (Standard: http://localhost:8081).
 */
public final class TestOrigin {

    private TestOrigin() {
    }

    public static String wert() {
        List<String> origins = ConfigProvider.getConfig().getValues("quarkus.webauthn.origins", String.class);
        return origins.get(0);
    }

    public static String host() {
        return URI.create(wert()).getHost();
    }
}
