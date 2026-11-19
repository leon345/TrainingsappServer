package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungId;
import de.trainingsapp.domain.workout.WorkoutEinladungStatus;
import de.trainingsapp.domain.workout.WorkoutId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workout_einladung")
public class WorkoutEinladungEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "workout_id", nullable = false)
    public UUID workoutId;

    @Column(name = "eingeladener_id", nullable = false)
    public UUID eingeladenerId;

    @Column(name = "status", nullable = false, length = 32)
    public String status;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    public static WorkoutEinladungEntity ausDomain(WorkoutEinladung einladung) {
        WorkoutEinladungEntity entity = new WorkoutEinladungEntity();
        entity.id = einladung.id().wert();
        entity.workoutId = einladung.workoutId().wert();
        entity.eingeladenerId = einladung.eingeladenerId().wert();
        entity.status = einladung.status().name();
        entity.erstelltAm = einladung.erstelltAm();
        return entity;
    }

    public WorkoutEinladung zuDomain() {
        return new WorkoutEinladung(
                new WorkoutEinladungId(id),
                new WorkoutId(workoutId),
                new BenutzerId(eingeladenerId),
                WorkoutEinladungStatus.valueOf(status),
                erstelltAm
        );
    }
}
