package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.workout.Satz;
import de.trainingsapp.domain.workout.SatzId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "satz")
public class SatzEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "uebung_id", nullable = false)
    public UUID uebungId;

    @Column(name = "position", nullable = false)
    public int position;

    @Column(name = "wiederholungen")
    public Integer wiederholungen;

    @Column(name = "dauer_sekunden")
    public Integer dauerSekunden;

    @Column(name = "gewicht_kg", precision = 6, scale = 2)
    public BigDecimal gewichtKg;

    @Column(name = "pause_sekunden")
    public Integer pauseSekunden;

    public static SatzEntity ausDomain(UUID uebungId, Satz satz) {
        SatzEntity entity = new SatzEntity();
        entity.id = satz.id().wert();
        entity.uebungId = uebungId;
        entity.position = satz.position();
        entity.wiederholungen = satz.wiederholungen();
        entity.dauerSekunden = satz.dauerSekunden();
        entity.gewichtKg = satz.gewichtKg();
        entity.pauseSekunden = satz.pauseSekunden();
        return entity;
    }

    public Satz zuDomain() {
        return new Satz(
                new SatzId(id),
                position,
                wiederholungen,
                dauerSekunden,
                gewichtKg,
                pauseSekunden
        );
    }
}
