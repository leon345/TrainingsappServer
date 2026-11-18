package de.trainingsapp.architektur;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * E9: Datenbank-Klassen nur in infrastructure. Domain und Application
 * (Haupt- und Testquellen) duerfen keine Persistenz-Technik importieren.
 */
class PersistenzSchichtenTest {

    private static final List<String> VERBOTEN = List.of(
            "jakarta.persistence",
            "jakarta.transaction",
            "org.hibernate",
            "io.quarkus.hibernate",
            "io.quarkus.panache",
            "org.flywaydb",
            "java.sql",
            "javax.sql",
            "io.agroal");

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/de/trainingsapp/domain",
            "src/main/java/de/trainingsapp/application",
            "src/test/java/de/trainingsapp/domain",
            "src/test/java/de/trainingsapp/application"})
    void keinePersistenzTechnikAusserhalbVonInfrastructure(String verzeichnis) throws IOException {
        Path wurzel = Path.of(verzeichnis);
        if (!Files.isDirectory(wurzel)) {
            return;
        }
        List<String> verstoesse;
        try (Stream<Path> dateien = Files.walk(wurzel)) {
            verstoesse = dateien
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(PersistenzSchichtenTest::verboteneImporte)
                    .toList();
        }
        assertEquals(List.of(), verstoesse);
    }

    private static Stream<String> verboteneImporte(Path datei) {
        try {
            return Files.readAllLines(datei).stream()
                    .map(String::trim)
                    .filter(zeile -> zeile.startsWith("import "))
                    .filter(zeile -> VERBOTEN.stream().anyMatch(v -> zeile.matches("import (static )?" + v.replace(".", "\\.") + "\\..*")))
                    .map(zeile -> datei + ": " + zeile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
