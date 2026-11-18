package de.trainingsapp.infrastructure.konfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * B4: Im Prod-Profil kommen Origins, rpId, Secure, Sitzungsschluessel und vertrauenswuerdige Proxys
 * ausschliesslich aus Umgebungsvariablen, ohne Standardwerte. Geprueft direkt an der Quelldatei
 * (wie ProdProfilZugangsdatenTest): Fehlt eine Variable, ist der Wert nicht aufloesbar, und der Start
 * scheitert an der Konfiguration; es gibt keinen Rueckfall auf die Werte fuer Dev und Test.
 */
class ProdUmgebungsvariablenTest {

    private static final Map<String, String> VARIABLEN = Map.of(
            "TRAININGSAPP_WEBAUTHN_ORIGINS", "https://app.example.de",
            "TRAININGSAPP_WEBAUTHN_RP_ID", "app.example.de",
            "TRAININGSAPP_COOKIE_SECURE", "true",
            "TRAININGSAPP_SITZUNG_SCHLUESSEL", "0123456789abcdef0123456789abcdef",
            "TRAININGSAPP_TRUSTED_PROXIES", "10.0.0.2");

    private static SmallRyeConfig prod(Map<String, String> umgebung) throws IOException {
        return new SmallRyeConfigBuilder()
                .withProfile("prod")
                .withSources(new PropertiesConfigSource(
                        Path.of("src/main/resources/application.properties").toUri().toURL()))
                .withSources(new PropertiesConfigSource(umgebung, "umgebung", 300))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quarkus.webauthn.origins",
            "quarkus.webauthn.relying-party.id",
            "trainingsapp.cookie.secure",
            "quarkus.http.auth.session.encryption-key",
            "quarkus.http.proxy.trusted-proxies"})
    void ohneUmgebungsvariableGibtEsKeinenWert(String schluessel) throws IOException {
        SmallRyeConfig config = prod(Map.of());
        assertThrows(NoSuchElementException.class, () -> config.getValue(schluessel, String.class), schluessel);
    }

    @Test
    void mitUmgebungsvariablenKommenGenauDiese() throws IOException {
        SmallRyeConfig config = prod(VARIABLEN);
        assertEquals(List.of("https://app.example.de"), config.getValues("quarkus.webauthn.origins", String.class));
        assertEquals("app.example.de", config.getValue("quarkus.webauthn.relying-party.id", String.class));
        assertEquals(true, config.getValue("trainingsapp.cookie.secure", Boolean.class));
        assertEquals("0123456789abcdef0123456789abcdef",
                config.getValue("quarkus.http.auth.session.encryption-key", String.class));
        assertEquals("10.0.0.2", config.getValue("quarkus.http.proxy.trusted-proxies", String.class));
        assertEquals(true, config.getValue("trainingsapp.q-sperre", Boolean.class));
    }

    /**
     * Eine leer gesetzte Variable gilt als fehlend: Der Schluessel waere dann "nicht konfiguriert",
     * und die Extension wuerde still einen Zufallsschluessel erzeugen. Das faengt
     * {@link ProdKonfigurationPruefung} beim Start ab (siehe ProdKonfigurationPruefungTest).
     */
    @Test
    void leereVariableGiltAlsFehlend() throws IOException {
        Map<String, String> umgebung = new HashMap<>(VARIABLEN);
        umgebung.put("TRAININGSAPP_SITZUNG_SCHLUESSEL", "");
        SmallRyeConfig config = prod(umgebung);
        Optional<String> schluessel;
        try {
            schluessel = config.getOptionalValue("quarkus.http.auth.session.encryption-key", String.class);
        } catch (NoSuchElementException e) {
            schluessel = Optional.empty();
        }
        assertEquals(Optional.empty(), schluessel);
        assertThrows(IllegalStateException.class, () -> ProdKonfigurationPruefung.pruefe(true, Optional.empty(), true));
    }
}
