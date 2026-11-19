package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Uebung;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutTyp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout")
public class WorkoutEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(name = "typ", nullable = false, length = 32)
    public String typ;

    @Column(name = "titel", nullable = false, length = 100)
    public String titel;

    @Column(name = "start_zeit", nullable = false)
    public Instant startZeit;

    @Column(name = "dauer_sekunden")
    public Integer dauerSekunden;

    @Column(name = "notiz", length = 500)
    public String notiz;

    @Column(name = "sichtbarkeit", nullable = false, length = 32)
    public String sichtbarkeit;

    @Column(name = "herkunft", nullable = false, length = 32)
    public String herkunft;

    @Column(name = "externe_id", length = 200)
    public String externeId;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "geaendert_am", nullable = false)
    public Instant geaendertAm;

    @Column(name = "version", nullable = false)
    public long version;

    public static WorkoutEntity ausDomain(Workout workout) {
        WorkoutEntity entity = new WorkoutEntity();
        entity.id = workout.id().wert();
        entity.ownerId = workout.ownerId().wert();
        entity.typ = workout.typ().name();
        entity.titel = workout.titel();
        entity.startZeit = workout.startZeit();
        entity.dauerSekunden = workout.dauerSekunden();
        entity.notiz = workout.notiz();
        entity.sichtbarkeit = workout.sichtbarkeit().name();
        entity.herkunft = workout.herkunft().name();
        entity.externeId = workout.externeId();
        entity.erstelltAm = workout.erstelltAm();
        entity.geaendertAm = workout.geaendertAm();
        entity.version = workout.version();
        return entity;
    }

    public Workout zuDomain(List<Uebung> uebungen) {
        return new Workout(
                new WorkoutId(id),
                new BenutzerId(ownerId),
                WorkoutTyp.valueOf(typ),
                titel,
                startZeit,
                dauerSekunden,
                notiz,
                Sichtbarkeit.valueOf(sichtbarkeit),
                Herkunft.valueOf(herkunft),
                externeId,
                uebungen,
                erstelltAm,
                geaendertAm,
                version
        );
    }
}
