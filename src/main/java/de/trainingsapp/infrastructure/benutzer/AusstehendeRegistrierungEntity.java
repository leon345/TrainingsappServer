package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.AusstehendeRegistrierung;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ausstehende_registrierung")
public class AusstehendeRegistrierungEntity {

    @Id
    @Column(name = "benutzer_id", nullable = false)
    public UUID benutzerId;

    @Column(name = "benutzername", nullable = false, unique = true, length = 32)
    public String benutzername;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "einwilligung_am", nullable = false)
    public Instant einwilligungAm;

    @Column(name = "laeuft_ab_am", nullable = false)
    public Instant laeuftAbAm;

    public static AusstehendeRegistrierungEntity ausDomain(AusstehendeRegistrierung registrierung) {
        AusstehendeRegistrierungEntity entity = new AusstehendeRegistrierungEntity();
        entity.benutzerId = registrierung.benutzerId().wert();
        entity.benutzername = registrierung.benutzername().wert();
        entity.erstelltAm = registrierung.erstelltAm();
        entity.einwilligungAm = registrierung.einwilligungAm();
        entity.laeuftAbAm = registrierung.laeuftAbAm();
        return entity;
    }

    public AusstehendeRegistrierung zuDomain() {
        return new AusstehendeRegistrierung(
                new BenutzerId(benutzerId),
                Benutzername.ausPersistenz(benutzername),
                erstelltAm,
                einwilligungAm,
                laeuftAbAm
        );
    }
}
