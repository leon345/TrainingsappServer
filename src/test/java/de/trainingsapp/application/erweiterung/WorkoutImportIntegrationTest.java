package de.trainingsapp.application.erweiterung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import de.trainingsapp.application.workout.SatzEingabe;
import de.trainingsapp.application.workout.UebungEingabe;
import de.trainingsapp.application.workout.WorkoutEingabe;
import de.trainingsapp.application.workout.WorkoutService;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.workout.WorkoutTyp;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WorkoutImportIntegrationTest {

    @Inject
    WorkoutImport workoutImport;

    @Inject
    WorkoutService workoutService;

    @Inject
    WorkoutRepository workoutRepository;

    @Inject
    DataSource dataSource;

    @Test
    void idempotentesUpsertImmerPrivatUndTeilindexGegenPostgreSql() throws SQLException {
        var nutzerA = TestBenutzerHelfer.registriereBenutzer("imp.a");
        var nutzerB = TestBenutzerHelfer.registriereBenutzer("imp.b");
        String externeId = "hk-workout-" + UUID.randomUUID();

        WorkoutImportDaten erstdaten = new WorkoutImportDaten(
                WorkoutTyp.CALISTHENICS,
                "Apple Health Calisthenics",
                Instant.parse("2026-11-19T12:00:00Z"),
                2100,
                "Erstimport",
                List.of(new UebungEingabe(
                        "Planche",
                        "Tuck",
                        List.of(new SatzEingabe(null, 15, null, 90))
                ))
        );

        // 1. Erstimport -> neu angelegt, PRIVAT, version=0
        Workout erster = workoutImport.importiere(nutzerA.id(), Herkunft.APPLE_HEALTH, externeId, erstdaten);
        assertEquals(Sichtbarkeit.PRIVAT, erster.sichtbarkeit());
        assertEquals(Herkunft.APPLE_HEALTH, erster.herkunft());
        assertEquals(externeId, erster.externeId());
        assertEquals(0L, erster.version());
        assertEquals(1, erster.uebungen().size());
        assertEquals("Tuck", erster.uebungen().getFirst().variante());

        // Nutzer A stellt Workout manuell auf OEFFENTLICH
        Workout oeffentlich = workoutService.aktualisiere(
                nutzerA.id(),
                erster.id(),
                erster.version(),
                new WorkoutEingabe(
                        erster.typ(),
                        erster.titel(),
                        erster.startZeit(),
                        erster.dauerSekunden(),
                        erster.notiz(),
                        Sichtbarkeit.OEFFENTLICH,
                        List.of()
                )
        );
        assertEquals(Sichtbarkeit.OEFFENTLICH, oeffentlich.sichtbarkeit());
        assertEquals(1L, oeffentlich.version());

        // 2. Erneuter Import mit identischem (ownerId, APPLE_HEALTH, externeId) -> Upsert, wieder PRIVAT!
        WorkoutImportDaten aktualisierteDaten = new WorkoutImportDaten(
                WorkoutTyp.KRAFT,
                "Apple Health Kraft (aktualisiert)",
                Instant.parse("2026-11-19T12:00:00Z"),
                2400,
                "Aktualisierter Import",
                List.of(new UebungEingabe(
                        "Bankdrücken",
                        null,
                        List.of(new SatzEingabe(8, null, new BigDecimal("75.00"), 120))
                ))
        );

        Workout zweiter = workoutImport.importiere(nutzerA.id(), Herkunft.APPLE_HEALTH, externeId, aktualisierteDaten);
        assertEquals(erster.id(), zweiter.id());
        assertEquals(2L, zweiter.version());
        assertEquals(Sichtbarkeit.PRIVAT, zweiter.sichtbarkeit());
        assertEquals("Apple Health Kraft (aktualisiert)", zweiter.titel());
        assertEquals(1, workoutRepository.findeAlleNachOwner(nutzerA.id(), null).size());

        // 3. Zweiter Benutzer mit derselben externeId erhält eigenes Workout
        Workout fuerNutzerB = workoutImport.importiere(nutzerB.id(), Herkunft.APPLE_HEALTH, externeId, erstdaten);
        assertEquals(nutzerB.id(), fuerNutzerB.ownerId());
        assertEquals(Sichtbarkeit.PRIVAT, fuerNutzerB.sichtbarkeit());

        // 4. Teilindex uq_workout_herkunft_extern existiert in PostgreSQL und verhindert SQL-Duplikate
        try (Connection verbindung = dataSource.getConnection()) {
            try (PreparedStatement idxCheck = verbindung.prepareStatement(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'workout' AND indexname = 'uq_workout_herkunft_extern'");
                 ResultSet rs = idxCheck.executeQuery()) {
                assertTrue(rs.next(), "Teilindex uq_workout_herkunft_extern muss in PostgreSQL existieren");
            }

            assertThrows(SQLException.class, () -> {
                try (PreparedStatement dupInsert = verbindung.prepareStatement(
                        "INSERT INTO workout (id, owner_id, typ, titel, start_zeit, sichtbarkeit, herkunft, externe_id, erstellt_am, geaendert_am, version) "
                                + "VALUES (?, ?, 'KRAFT', 'Duplikat', now(), 'PRIVAT', 'APPLE_HEALTH', ?, now(), now(), 0)")) {
                    dupInsert.setObject(1, UUID.randomUUID());
                    dupInsert.setObject(2, nutzerA.id().wert());
                    dupInsert.setString(3, externeId);
                    dupInsert.executeUpdate();
                }
            });
        }

        // 5. Ungültige Importanfragen werden abgelehnt
        assertThrows(
                DomainException.class,
                () -> workoutImport.importiere(nutzerA.id(), Herkunft.MANUELL, "ext-manuell", erstdaten)
        );
        assertThrows(
                DomainException.class,
                () -> workoutImport.importiere(nutzerA.id(), Herkunft.APPLE_HEALTH, "", erstdaten)
        );
    }
}
