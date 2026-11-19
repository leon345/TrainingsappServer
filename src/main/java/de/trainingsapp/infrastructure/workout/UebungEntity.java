package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.workout.Satz;
import de.trainingsapp.domain.workout.Uebung;
import de.trainingsapp.domain.workout.UebungId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "uebung")
public class UebungEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "workout_id", nullable = false)
    public UUID workoutId;

    @Column(name = "position", nullable = false)
    public int position;

    @Column(name = "name", nullable = false, length = 100)
    public String name;

    @Column(name = "variante", length = 100)
    public String variante;

    public static UebungEntity ausDomain(UUID workoutId, Uebung uebung) {
        UebungEntity entity = new UebungEntity();
        entity.id = uebung.id().wert();
        entity.workoutId = workoutId;
        entity.position = uebung.position();
        entity.name = uebung.name();
        entity.variante = uebung.variante();
        return entity;
    }

    public Uebung zuDomain(List<Satz> saetze) {
        return new Uebung(
                new UebungId(id),
                position,
                name,
                variante,
                saetze
        );
    }
}
