package de.trainingsapp.infrastructure.http;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Testprofil mit dem Konfigurationsprofil {@code prod}. Die Werte, die in Prod aus
 * Umgebungsvariablen kommen, werden hier als gleichnamige Konfigurationswerte gesetzt. Die Datenbank
 * kommt in Tests weiter von Dev Services; Flyway migriert wie in Prod beim Start, vorher wird geleert.
 */
public class ProdProfil implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "prod";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "TRAININGSAPP_WEBAUTHN_ORIGINS", "http://localhost:8081",
                "TRAININGSAPP_WEBAUTHN_RP_ID", "localhost",
                "TRAININGSAPP_TRUSTED_PROXIES", "127.0.0.1",
                "quarkus.flyway.clean-disabled", "false",
                "quarkus.flyway.clean-at-start", "true",
                "quarkus.hibernate-orm.statistics", "true",
                // Die Test-Entity PlatzhalterEntity (S-12) braucht ihre Tabelle aus der Test-Migration
                "quarkus.flyway.locations", "db/migration,db/test-migration");
    }
}
