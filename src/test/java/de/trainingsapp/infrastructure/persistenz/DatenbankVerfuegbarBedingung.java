package de.trainingsapp.infrastructure.persistenz;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Fuehrt Datenbank-Tests nur aus, wenn die Test-Datenbank erreichbar ist.
 *
 * <p>Steuerung ueber die System-Property {@code datenbank.tests}:
 * <ul>
 *   <li>nicht gesetzt: automatisch, Test wird uebersprungen, wenn der Host der
 *       Test-JDBC-URL nicht per TCP erreichbar ist,</li>
 *   <li>{@code pflicht}: Test laeuft immer (schlaegt ohne Datenbank fehl), fuer CI,</li>
 *   <li>{@code aus}: Test wird immer uebersprungen.</li>
 * </ul>
 */
public class DatenbankVerfuegbarBedingung implements ExecutionCondition {

    static final String SCHALTER = "datenbank.tests";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String modus = System.getProperty(SCHALTER, "auto");
        if ("pflicht".equals(modus)) {
            return ConditionEvaluationResult.enabled(SCHALTER + "=pflicht");
        }
        if ("aus".equals(modus)) {
            return ConditionEvaluationResult.disabled(SCHALTER + "=aus");
        }
        String url = testJdbcUrl();
        URI uri = URI.create(url.substring("jdbc:".length()));
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), 1000);
            return ConditionEvaluationResult.enabled("Datenbank erreichbar: " + uri.getHost() + ":" + port);
        } catch (IOException e) {
            return ConditionEvaluationResult.disabled("UEBERSPRUNGEN: keine PostgreSQL unter "
                    + uri.getHost() + ":" + port + " erreichbar (siehe docs/persistenz.md)");
        }
    }

    private static String testJdbcUrl() {
        Optional<String> ausUmgebung = Optional.ofNullable(System.getenv("QUARKUS_DATASOURCE_JDBC_URL"))
                .or(() -> Optional.ofNullable(System.getProperty("quarkus.datasource.jdbc.url")));
        if (ausUmgebung.isPresent()) {
            return ausUmgebung.get();
        }
        try {
            SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .withProfile("test")
                    .withSources(new PropertiesConfigSource(
                            Path.of(ProdProfilZugangsdatenTest.ANWENDUNGSKONFIGURATION).toUri().toURL()))
                    .build();
            return config.getValue("quarkus.datasource.jdbc.url", String.class);
        } catch (IOException e) {
            throw new IllegalStateException("application.properties nicht lesbar", e);
        }
    }
}
