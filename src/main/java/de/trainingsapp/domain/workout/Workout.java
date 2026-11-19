package de.trainingsapp.domain.workout;

import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregatwurzel für ein Kraft- oder Calisthenics-Workout inkl. Übungen, Sätzen und optimistischer Versionierung.
 */
public record Workout(
        WorkoutId id,
        BenutzerId ownerId,
        WorkoutTyp typ,
        String titel,
        Instant startZeit,
        Integer dauerSekunden,
        String notiz,
        Sichtbarkeit sichtbarkeit,
        Herkunft herkunft,
        String externeId,
        List<Uebung> uebungen,
        Instant erstelltAm,
        Instant geaendertAm,
        long version
) {

    public static final String FEHLERCODE = "UNGUELTIGES_WORKOUT";
    public static final int MAX_TITEL_LAENGE = 100;
    public static final int MAX_NOTIZ_LAENGE = 500;
    public static final int MAX_EXTERNE_ID_LAENGE = 200;
    public static final int MIN_DAUER_SEKUNDEN = 1;
    public static final int MAX_DAUER_SEKUNDEN = 86_400;

    public Workout {
        Objects.requireNonNull(id, "WorkoutId darf nicht null sein");
        Objects.requireNonNull(ownerId, "ownerId darf nicht null sein");
        if (typ == null) {
            throw new DomainException(FEHLERCODE, "Der Workout-Typ darf nicht fehlen.");
        }
        if (titel == null || titel.isBlank()) {
            throw new DomainException(FEHLERCODE, "Der Workout-Titel darf nicht leer sein.");
        }
        String bereinigterTitel = titel.trim();
        if (bereinigterTitel.length() > MAX_TITEL_LAENGE) {
            throw new DomainException(
                    FEHLERCODE,
                    "Der Workout-Titel darf höchstens " + MAX_TITEL_LAENGE + " Zeichen lang sein."
            );
        }
        titel = bereinigterTitel;

        if (startZeit == null) {
            throw new DomainException(FEHLERCODE, "Die Startzeit darf nicht fehlen.");
        }
        if (dauerSekunden != null && (dauerSekunden < MIN_DAUER_SEKUNDEN || dauerSekunden > MAX_DAUER_SEKUNDEN)) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Workout-Dauer muss zwischen " + MIN_DAUER_SEKUNDEN + " und "
                            + MAX_DAUER_SEKUNDEN + " Sekunden liegen."
            );
        }

        if (notiz == null || notiz.isBlank()) {
            notiz = null;
        } else {
            String bereinigteNotiz = notiz.trim();
            if (bereinigteNotiz.length() > MAX_NOTIZ_LAENGE) {
                throw new DomainException(
                        FEHLERCODE,
                        "Die Notiz darf höchstens " + MAX_NOTIZ_LAENGE + " Zeichen lang sein."
                );
            }
            notiz = bereinigteNotiz;
        }

        if (sichtbarkeit == null) {
            throw new DomainException(FEHLERCODE, "Die Sichtbarkeit darf nicht fehlen.");
        }
        if (herkunft == null) {
            throw new DomainException(FEHLERCODE, "Die Herkunft darf nicht fehlen.");
        }
        if (herkunft == Herkunft.MANUELL) {
            if (externeId != null) {
                throw new DomainException(
                        FEHLERCODE,
                        "Manuelle Workouts dürfen keine externe ID tragen."
                );
            }
        } else {
            if (externeId == null || externeId.isBlank()) {
                throw new DomainException(
                        FEHLERCODE,
                        "Importierte Workouts benötigen eine nicht-leere externe ID."
                );
            }
            String bereinigteExterneId = externeId.trim();
            if (bereinigteExterneId.length() > MAX_EXTERNE_ID_LAENGE) {
                throw new DomainException(
                        FEHLERCODE,
                        "Die externe ID darf höchstens " + MAX_EXTERNE_ID_LAENGE + " Zeichen lang sein."
                );
            }
            externeId = bereinigteExterneId;
        }

        uebungen = uebungen == null ? List.of() : List.copyOf(uebungen);
        Objects.requireNonNull(erstelltAm, "erstelltAm darf nicht null sein");
        Objects.requireNonNull(geaendertAm, "geaendertAm darf nicht null sein");
        if (version < 0) {
            throw new DomainException(FEHLERCODE, "Die Version darf nicht negativ sein.");
        }
    }

    public boolean istOeffentlich() {
        return sichtbarkeit == Sichtbarkeit.OEFFENTLICH;
    }

    public Workout aktualisieren(
            WorkoutTyp neuerTyp,
            String neuerTitel,
            Instant neueStartZeit,
            Integer neueDauerSekunden,
            String neueNotiz,
            Sichtbarkeit neueSichtbarkeit,
            List<Uebung> neueUebungen,
            Instant zeitpunkt
    ) {
        return new Workout(
                id,
                ownerId,
                neuerTyp,
                neuerTitel,
                neueStartZeit,
                neueDauerSekunden,
                neueNotiz,
                neueSichtbarkeit,
                herkunft,
                externeId,
                neueUebungen,
                erstelltAm,
                zeitpunkt,
                version + 1
        );
    }

    public Workout alsImportAktualisieren(
            WorkoutTyp neuerTyp,
            String neuerTitel,
            Instant neueStartZeit,
            Integer neueDauerSekunden,
            String neueNotiz,
            List<Uebung> neueUebungen,
            Instant zeitpunkt
    ) {
        return aktualisieren(
                neuerTyp,
                neuerTitel,
                neueStartZeit,
                neueDauerSekunden,
                neueNotiz,
                Sichtbarkeit.PRIVAT,
                neueUebungen,
                zeitpunkt
        );
    }
}
