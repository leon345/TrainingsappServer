package de.trainingsapp.infrastructure.platzhalter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Nur fuer Tests: JPA-Entity zur Tabelle {@code platzhalter}
 * (angelegt von db/test-migration). Verlaesst die Infrastructure-Schicht nie.
 */
@Entity
@Table(name = "platzhalter")
public class PlatzhalterEntity {

    @Id
    public UUID id;

    public String name;
}
