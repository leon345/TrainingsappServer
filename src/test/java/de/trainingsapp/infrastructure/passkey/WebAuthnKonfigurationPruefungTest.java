package de.trainingsapp.infrastructure.passkey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Allowlist fuer Origins und rpId (E23, Abnahme 7). */
class WebAuthnKonfigurationPruefungTest {

    @Test
    void gueltigeKonfigurationen() {
        assertDoesNotThrow(() -> WebAuthnKonfigurationPruefung.pruefe(
                List.of("https://app.example.de"), "app.example.de"));
        assertDoesNotThrow(() -> WebAuthnKonfigurationPruefung.pruefe(
                List.of("https://app.example.de", "https://www.example.de:8443"), "example.de"));
        assertDoesNotThrow(() -> WebAuthnKonfigurationPruefung.pruefe(List.of("http://localhost:8081"), "localhost"));
    }

    @Test
    void leereAllowlistOderRpIdBrichtAb() {
        assertThrows(IllegalStateException.class, () -> WebAuthnKonfigurationPruefung.pruefe(List.of(), "example.de"));
        assertThrows(IllegalStateException.class,
                () -> WebAuthnKonfigurationPruefung.pruefe(List.of("https://example.de"), ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://app.example.de", "https://app.example.de/", "https://app.example.de/pfad",
            "https://*.example.de", "*", "app.example.de", "https://evil.example", "https://example.de.evil.example",
            "https://user@app.example.de", "https://app.example.de?x=1"})
    void unzulaessigeOrigins(String origin) {
        assertThrows(IllegalStateException.class,
                () -> WebAuthnKonfigurationPruefung.pruefe(List.of(origin), "example.de"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.de", "example.de:443", "EXAMPLE.DE", "*.example.de"})
    void unzulaessigeRpIds(String rpId) {
        assertThrows(IllegalStateException.class,
                () -> WebAuthnKonfigurationPruefung.pruefe(List.of("https://example.de"), rpId));
    }
}
