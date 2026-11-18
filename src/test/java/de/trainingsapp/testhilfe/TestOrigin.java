package de.trainingsapp.testhilfe;

import org.eclipse.microprofile.config.ConfigProvider;

/** Origin des Testservers, wie ihn ein Browser fuer Seiten dieses Servers sendet. */
public final class TestOrigin {

    private TestOrigin() {
    }

    public static String wert() {
        int port = ConfigProvider.getConfig().getOptionalValue("quarkus.http.test-port", Integer.class).orElse(8081);
        return "http://localhost:" + port;
    }
}
