package de.trainingsapp.infrastructure.konfiguration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Kein stiller Rueckfall auf einen Zufallsschluessel oder unsichere Cookies im Prod-Profil. */
class ProdKonfigurationPruefungTest {

    private static final Optional<String> GUELTIG = Optional.of("x".repeat(32));

    @Test
    void prodMitVollstaendigerKonfigurationStartet() {
        assertDoesNotThrow(() -> ProdKonfigurationPruefung.pruefe(true, GUELTIG, true));
    }

    @Test
    void prodOhneOderMitLeeremSchluesselBrichtAb() {
        assertThrows(IllegalStateException.class, () -> ProdKonfigurationPruefung.pruefe(true, Optional.empty(), true));
        assertThrows(IllegalStateException.class, () -> ProdKonfigurationPruefung.pruefe(true, Optional.of(" "), true));
    }

    @Test
    void prodMitZuKurzemSchluesselBrichtAb() {
        assertThrows(IllegalStateException.class,
                () -> ProdKonfigurationPruefung.pruefe(true, Optional.of("x".repeat(31)), true));
    }

    @Test
    void prodOhneSecureBrichtAb() {
        assertThrows(IllegalStateException.class, () -> ProdKonfigurationPruefung.pruefe(true, GUELTIG, false));
    }

    @Test
    void devUndTestBleibenUnberuehrt() {
        assertDoesNotThrow(() -> ProdKonfigurationPruefung.pruefe(false, Optional.empty(), false));
    }
}
