package de.trainingsapp.infrastructure.persistenz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueft die Profiltrennung (E9/E12) direkt an application.properties, ohne Datenbank:
 * Im Prod-Profil liefert die Datei weder JDBC-URL noch Benutzer noch Passwort,
 * sie muessen aus Umgebungsvariablen kommen. Dev und Test haben eigene Werte.
 */
class ProdProfilZugangsdatenTest {

    /** Bewusst die Quelldatei: Quarkus-Tests legen eigene application.properties in den Klassenpfad. */
    static final String ANWENDUNGSKONFIGURATION = "src/main/resources/application.properties";

    private static final List<String> ZUGANGSDATEN = List.of(
            "quarkus.datasource.jdbc.url",
            "quarkus.datasource.username",
            "quarkus.datasource.password");

    private static SmallRyeConfig konfigurationAusDatei(String profil) throws IOException {
        return new SmallRyeConfigBuilder()
                .withProfile(profil)
                .withSources(new PropertiesConfigSource(
                        Path.of(ANWENDUNGSKONFIGURATION).toUri().toURL()))
                .build();
    }

    @Test
    void prodProfilEnthaeltKeineZugangsdaten() throws IOException {
        SmallRyeConfig prod = konfigurationAusDatei("prod");
        for (String schluessel : ZUGANGSDATEN) {
            assertEquals(Optional.empty(), prod.getOptionalValue(schluessel, String.class),
                    schluessel + " darf im Prod-Profil nicht aus application.properties kommen");
        }
    }

    @Test
    void prodProfilErlaubtKeinLeerenDerDatenbank() throws IOException {
        SmallRyeConfig prod = konfigurationAusDatei("prod");
        assertEquals(Optional.empty(), prod.getOptionalValue("quarkus.flyway.clean-at-start", Boolean.class));
        assertEquals(Optional.empty(), prod.getOptionalValue("quarkus.flyway.clean-disabled", Boolean.class));
        assertEquals(Optional.of(true), prod.getOptionalValue("quarkus.flyway.migrate-at-start", Boolean.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void devUndTestHabenEigeneZugangsdaten(String profil) throws IOException {
        SmallRyeConfig config = konfigurationAusDatei(profil);
        for (String schluessel : ZUGANGSDATEN) {
            assertTrue(config.getOptionalValue(schluessel, String.class).isPresent(),
                    schluessel + " fehlt im Profil " + profil);
        }
    }
}
