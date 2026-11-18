package de.trainingsapp.infrastructure.benutzer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA-Entity zur Tabelle {@code benutzer} (V2). Verlaesst die Infrastructure-Schicht nie. */
@Entity
@Table(name = "benutzer")
public class BenutzerEntity {

    @Id
    public UUID id;

    @Column(nullable = false, length = 64)
    public String benutzername;
}
