package de.trainingsapp.domain.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkoutDomainTest {

    private static final Instant JETZT = Instant.parse("2026-11-19T18:00:00Z");

    @Test
    void satzBenoetigtMindestensWiederholungenOderDauerUndPrueftGrenzen() {
        // Nur Wiederholungen gültig
        Satz kraftSatz = new Satz(SatzId.neu(), 0, 8, null, new BigDecimal("82.50"), 120);
        assertEquals(8, kraftSatz.wiederholungen());
        assertNull(kraftSatz.dauerSekunden());

        // Nur Haltedauer gültig (Calisthenics)
        Satz halteSatz = new Satz(SatzId.neu(), 1, null, 25, BigDecimal.ZERO, 90);
        assertEquals(25, halteSatz.dauerSekunden());

        // Beide Werte gleichzeitig gültig
        Satz kombiSatz = new Satz(SatzId.neu(), 2, 10, 45, new BigDecimal("10.25"), 0);
        assertEquals(10, kombiSatz.wiederholungen());
        assertEquals(45, kombiSatz.dauerSekunden());

        // Weder Wiederholungen noch Dauer -> DomainException
        DomainException ohneLeistung = assertThrows(
                DomainException.class,
                () -> new Satz(SatzId.neu(), 0, null, null, new BigDecimal("20"), 60)
        );
        assertEquals(Satz.FEHLERCODE, ohneLeistung.code());

        // Ungültige Grenzwerte
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), -1, 5, null, null, null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 0, null, null, null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 10_001, null, null, null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, null, 0, null, null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, null, 86_401, null, null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 5, null, new BigDecimal("-0.50"), null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 5, null, new BigDecimal("10000.00"), null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 5, null, new BigDecimal("12.345"), null));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 5, null, null, -1));
        assertThrows(DomainException.class, () -> new Satz(SatzId.neu(), 0, 5, null, null, 86_401));
    }

    @Test
    void uebungTrimmtNameUndVarianteUndPrueftLaengen() {
        Uebung cali = new Uebung(UebungId.neu(), 0, "  Front Lever  ", "  Tuck  ", List.of());
        assertEquals("Front Lever", cali.name());
        assertEquals("Tuck", cali.variante());

        Uebung ohneVariante = new Uebung(UebungId.neu(), 1, "Klimmzüge", "   ", List.of());
        assertNull(ohneVariante.variante());

        assertThrows(DomainException.class, () -> new Uebung(UebungId.neu(), 0, "   ", null, List.of()));
        assertThrows(DomainException.class, () -> new Uebung(UebungId.neu(), 0, "a".repeat(101), null, List.of()));
        assertThrows(DomainException.class, () -> new Uebung(UebungId.neu(), 0, "Dips", "v".repeat(101), List.of()));
        assertThrows(DomainException.class, () -> new Uebung(UebungId.neu(), -1, "Dips", null, List.of()));
    }

    @Test
    void workoutValidiertTitelNotizDauerHerkunftUndVersion() {
        BenutzerId owner = BenutzerId.neu();

        Workout manuell = new Workout(
                WorkoutId.neu(),
                owner,
                WorkoutTyp.KRAFT,
                "  Ganzkörper  ",
                JETZT,
                2700,
                "  Starkes Training  ",
                Sichtbarkeit.PRIVAT,
                Herkunft.MANUELL,
                null,
                List.of(),
                JETZT,
                JETZT,
                0L
        );
        assertEquals("Ganzkörper", manuell.titel());
        assertEquals("Starkes Training", manuell.notiz());

        Workout aktualisiert = manuell.aktualisieren(
                WorkoutTyp.CALISTHENICS,
                "Skills",
                JETZT,
                1800,
                null,
                Sichtbarkeit.OEFFENTLICH,
                List.of(),
                JETZT.plusSeconds(60)
        );
        assertEquals(1L, aktualisiert.version());
        assertEquals(Sichtbarkeit.OEFFENTLICH, aktualisiert.sichtbarkeit());

        Workout importAktualisiert = aktualisiert.alsImportAktualisieren(
                WorkoutTyp.CALISTHENICS,
                "Skills Import",
                JETZT,
                1800,
                null,
                List.of(),
                JETZT.plusSeconds(120)
        );
        assertEquals(2L, importAktualisiert.version());
        assertEquals(Sichtbarkeit.PRIVAT, importAktualisiert.sichtbarkeit());

        // Titel leer / zu lang
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, " ", 600, null, Herkunft.MANUELL, null));
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "t".repeat(101), 600, null, Herkunft.MANUELL, null));
        // Notiz zu lang
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 600, "n".repeat(501), Herkunft.MANUELL, null));
        // Dauer ungültig
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 0, null, Herkunft.MANUELL, null));
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 86_401, null, Herkunft.MANUELL, null));
        // Manuell mit externer ID verboten
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 600, null, Herkunft.MANUELL, "ext-1"));
        // Extern ohne externe ID oder zu lang verboten
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 600, null, Herkunft.APPLE_HEALTH, null));
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 600, null, Herkunft.APPLE_HEALTH, "   "));
        assertThrows(DomainException.class, () -> erstelleWorkout(owner, "OK", 600, null, Herkunft.APPLE_HEALTH, "x".repeat(201)));
    }

    private Workout erstelleWorkout(
            BenutzerId owner,
            String titel,
            Integer dauerSekunden,
            String notiz,
            Herkunft herkunft,
            String externeId
    ) {
        return new Workout(
                WorkoutId.neu(),
                owner,
                WorkoutTyp.KRAFT,
                titel,
                JETZT,
                dauerSekunden,
                notiz,
                Sichtbarkeit.PRIVAT,
                herkunft,
                externeId,
                List.of(),
                JETZT,
                JETZT,
                0L
        );
    }
}
