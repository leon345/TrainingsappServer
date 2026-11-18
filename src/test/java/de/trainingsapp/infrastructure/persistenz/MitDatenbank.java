package de.trainingsapp.infrastructure.persistenz;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Testprofil fuer Tests, die eine echte PostgreSQL-Datenbank brauchen:
 * leert die Testdatenbank, wendet alle Flyway-Migrationen beim Start an und
 * laesst Hibernate das Schema gegen die Entities pruefen.
 */
public class MitDatenbank implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.flyway.migrate-at-start", "true",
                "quarkus.flyway.clean-disabled", "false",
                "quarkus.flyway.clean-at-start", "true",
                "quarkus.hibernate-orm.schema-management.strategy", "validate");
    }
}
