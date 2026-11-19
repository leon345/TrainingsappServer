package de.trainingsapp.infrastructure.timer;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.timer.IntervallEinstellung;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timer_einstellung")
public class TimerEinstellungEntity {

    @Id
    @Column(name = "benutzer_id", nullable = false)
    public UUID benutzerId;

    @Column(name = "training_sekunden", nullable = false)
    public int trainingSekunden;

    @Column(name = "pause_sekunden", nullable = false)
    public int pauseSekunden;

    @Column(name = "geaendert_am", nullable = false)
    public Instant geaendertAm;

    public static TimerEinstellungEntity ausDomain(
            BenutzerId benutzerId,
            IntervallEinstellung einstellung,
            Instant geaendertAm
    ) {
        TimerEinstellungEntity entity = new TimerEinstellungEntity();
        entity.benutzerId = benutzerId.wert();
        entity.trainingSekunden = einstellung.trainingSekunden();
        entity.pauseSekunden = einstellung.pauseSekunden();
        entity.geaendertAm = geaendertAm;
        return entity;
    }

    public IntervallEinstellung zuDomain() {
        return new IntervallEinstellung(trainingSekunden, pauseSekunden);
    }
}
