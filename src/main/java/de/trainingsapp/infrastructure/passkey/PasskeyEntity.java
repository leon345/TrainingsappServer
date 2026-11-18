package de.trainingsapp.infrastructure.passkey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA-Entity zur Tabelle {@code passkey} (V2). {@code id} ist ein technischer Schluessel
 * nach der Konvention aus docs/persistenz.md; fachlich identifiziert die Credential-ID den Passkey.
 */
@Entity
@Table(name = "passkey")
public class PasskeyEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    public String credentialId;

    @Column(nullable = false)
    public UUID benutzerId;

    @Column(nullable = false, length = 50)
    public String bezeichnung;

    @Column(nullable = false)
    public byte[] publicKey;

    @Column(nullable = false)
    public long algorithmus;

    @Column(nullable = false)
    public long zaehler;

    @Column(nullable = false)
    public LocalDate erstelltAm;

    public LocalDate zuletztVerwendetAm;
}
