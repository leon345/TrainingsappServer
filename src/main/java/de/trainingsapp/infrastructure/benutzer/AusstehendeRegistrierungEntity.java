package de.trainingsapp.infrastructure.benutzer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA-Entity zur Tabelle {@code ausstehende_registrierung} (V3). {@code id} ist die vorgesehene BenutzerId. */
@Entity
@Table(name = "ausstehende_registrierung")
public class AusstehendeRegistrierungEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true, length = 32)
    public String benutzername;

    @Column(nullable = false)
    public Instant gueltigBis;
}
