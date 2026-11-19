package de.trainingsapp.application.erweiterung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.trainingsapp.application.workout.SatzEingabe;
import de.trainingsapp.application.workout.UebungEingabe;
import de.trainingsapp.application.zeit.Uhr;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.workout.WorkoutTyp;
import de.trainingsapp.domain.workout.WorkoutVersionskonfliktException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkoutImportServiceTest {

    @Test
    void importIstIdempotentImmerPrivatUndTrenntNachBenutzer() {
        InMemoryWorkoutRepository repo = new InMemoryWorkoutRepository();
        AtomicReference<Instant> zeit = new AtomicReference<>(Instant.parse("2026-11-19T10:00:00Z"));
        Uhr uhr = zeit::get;
        WorkoutImportService service = new WorkoutImportService(repo, uhr);

        BenutzerId nutzerA = BenutzerId.neu();
        BenutzerId nutzerB = BenutzerId.neu();

        WorkoutImportDaten erstdaten = new WorkoutImportDaten(
                WorkoutTyp.CALISTHENICS,
                "Ring-Training",
                Instant.parse("2026-11-19T09:00:00Z"),
                2400,
                "Aus Apple Health",
                List.of(new UebungEingabe(
                        "Front Lever",
                        "Tuck",
                        List.of(new SatzEingabe(null, 20, null, 90))
                ))
        );

        Workout importiert1 = service.importiere(nutzerA, Herkunft.APPLE_HEALTH, "  hk-123  ", erstdaten);
        assertEquals(Sichtbarkeit.PRIVAT, importiert1.sichtbarkeit());
        assertEquals(Herkunft.APPLE_HEALTH, importiert1.herkunft());
        assertEquals("hk-123", importiert1.externeId());
        assertEquals(0L, importiert1.version());
        assertEquals(1, repo.alle().size());

        // Simuliere zwischenzeitliches Öffentlich-Schalten: erneuter Import muss wieder PRIVAT erzwingen
        Workout oeffentlich = importiert1.aktualisieren(
                importiert1.typ(),
                importiert1.titel(),
                importiert1.startZeit(),
                importiert1.dauerSekunden(),
                importiert1.notiz(),
                Sichtbarkeit.OEFFENTLICH,
                importiert1.uebungen(),
                zeit.get().plusSeconds(30)
        );
        repo.aktualisieren(oeffentlich, importiert1.version());

        zeit.set(Instant.parse("2026-11-19T10:05:00Z"));
        WorkoutImportDaten updateDaten = new WorkoutImportDaten(
                WorkoutTyp.KRAFT,
                "Ring-Training aktualisiert",
                Instant.parse("2026-11-19T09:00:00Z"),
                2700,
                "Korrigierte Dauer",
                List.of(new UebungEingabe(
                        "Klimmzüge",
                        "Weighted",
                        List.of(new SatzEingabe(6, null, new BigDecimal("15.00"), 120))
                ))
        );

        Workout importiert2 = service.importiere(nutzerA, Herkunft.APPLE_HEALTH, "hk-123", updateDaten);
        assertEquals(importiert1.id(), importiert2.id());
        assertEquals(2L, importiert2.version());
        assertEquals("Ring-Training aktualisiert", importiert2.titel());
        assertEquals(Sichtbarkeit.PRIVAT, importiert2.sichtbarkeit());
        assertEquals(1, repo.alle().size());

        // Gleiche externeId bei anderem Benutzer -> eigener Datensatz
        Workout fuerNutzerB = service.importiere(nutzerB, Herkunft.APPLE_HEALTH, "hk-123", erstdaten);
        assertEquals(nutzerB, fuerNutzerB.ownerId());
        assertEquals(2, repo.alle().size());
    }

    @Test
    void importLehntManuellUndUngueltigeEingabenAb() {
        InMemoryWorkoutRepository repo = new InMemoryWorkoutRepository();
        WorkoutImportService service = new WorkoutImportService(repo, () -> Instant.parse("2026-11-19T10:00:00Z"));
        BenutzerId nutzer = BenutzerId.neu();
        WorkoutImportDaten gueltig = new WorkoutImportDaten(
                WorkoutTyp.KRAFT,
                "Push",
                Instant.parse("2026-11-19T09:00:00Z"),
                1800,
                null,
                List.of()
        );

        DomainException manuellFehler = assertThrows(
                DomainException.class,
                () -> service.importiere(nutzer, Herkunft.MANUELL, "ext-1", gueltig)
        );
        assertEquals("UNGUELTIGE_HERKUNFT", manuellFehler.code());

        DomainException ohneExterneId = assertThrows(
                DomainException.class,
                () -> service.importiere(nutzer, Herkunft.APPLE_HEALTH, "   ", gueltig)
        );
        assertEquals("UNGUELTIGE_EXTERNE_ID", ohneExterneId.code());

        assertThrows(
                DomainException.class,
                () -> service.importiere(nutzer, Herkunft.APPLE_HEALTH, "x".repeat(201), gueltig)
        );

        // Domain-Validierung greift genauso wie bei manueller Eingabe
        WorkoutImportDaten ungueltigerSatz = new WorkoutImportDaten(
                WorkoutTyp.KRAFT,
                "Push",
                Instant.parse("2026-11-19T09:00:00Z"),
                1800,
                null,
                List.of(new UebungEingabe("Bankdrücken", null, List.of(new SatzEingabe(null, null, null, null))))
        );
        assertThrows(
                DomainException.class,
                () -> service.importiere(nutzer, Herkunft.APPLE_HEALTH, "ext-2", ungueltigerSatz)
        );
        assertEquals(0, repo.alle().size());
    }

    private static class InMemoryWorkoutRepository implements WorkoutRepository {

        private final Map<WorkoutId, Workout> speicher = new HashMap<>();

        List<Workout> alle() {
            return new ArrayList<>(speicher.values());
        }

        @Override
        public Optional<Workout> findeNachId(WorkoutId id) {
            return Optional.ofNullable(speicher.get(id));
        }

        @Override
        public List<Workout> findeAlleNachIds(java.util.Collection<WorkoutId> ids) {
            return ids.stream()
                    .map(speicher::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Workout::startZeit).reversed())
                    .toList();
        }

        @Override
        public List<Workout> findeAlleNachOwner(BenutzerId ownerId, WorkoutTyp optionalerTyp) {
            return speicher.values().stream()
                    .filter(w -> w.ownerId().equals(ownerId))
                    .filter(w -> optionalerTyp == null || w.typ() == optionalerTyp)
                    .sorted(Comparator.comparing(Workout::startZeit).reversed())
                    .toList();
        }

        @Override
        public List<Workout> findeOeffentlicheNachOwner(BenutzerId ownerId) {
            return speicher.values().stream()
                    .filter(w -> w.ownerId().equals(ownerId) && w.istOeffentlich())
                    .sorted(Comparator.comparing(Workout::startZeit).reversed())
                    .toList();
        }

        @Override
        public Optional<Workout> findeNachOwnerUndHerkunftUndExterneId(
                BenutzerId ownerId,
                Herkunft herkunft,
                String externeId
        ) {
            return speicher.values().stream()
                    .filter(w -> w.ownerId().equals(ownerId)
                            && w.herkunft() == herkunft
                            && externeId.equals(w.externeId()))
                    .findFirst();
        }

        @Override
        public void speichern(Workout workout) {
            speicher.put(workout.id(), workout);
        }

        @Override
        public Workout aktualisieren(Workout aktualisiertesWorkout, long erwarteteVersion) {
            Workout vorhanden = speicher.get(aktualisiertesWorkout.id());
            if (vorhanden == null || vorhanden.version() != erwarteteVersion) {
                throw new WorkoutVersionskonfliktException();
            }
            speicher.put(aktualisiertesWorkout.id(), aktualisiertesWorkout);
            return aktualisiertesWorkout;
        }

        @Override
        public Workout aktualisiereSichtbarkeit(Workout aktualisiertesWorkout, long erwarteteVersion) {
            return aktualisieren(aktualisiertesWorkout, erwarteteVersion);
        }

        @Override
        public boolean loeschen(WorkoutId id) {
            return speicher.remove(id) != null;
        }
    }
}
