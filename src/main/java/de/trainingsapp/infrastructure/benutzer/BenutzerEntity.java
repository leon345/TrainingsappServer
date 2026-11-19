package de.trainingsapp.infrastructure.benutzer;

import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;
import de.trainingsapp.domain.benutzer.Rolle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "benutzer")
public class BenutzerEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "benutzername", nullable = false, unique = true, length = 32)
    public String benutzername;

    @Enumerated(EnumType.STRING)
    @Column(name = "rolle", nullable = false, length = 20)
    public Rolle rolle;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "einwilligung_am", nullable = false)
    public Instant einwilligungAm;

    @Column(name = "naechste_passkey_nummer", nullable = false)
    public int naechstePasskeyNummer;

    public static BenutzerEntity ausDomain(Benutzer benutzer) {
        BenutzerEntity entity = new BenutzerEntity();
        entity.id = benutzer.id().wert();
        entity.benutzername = benutzer.benutzername().wert();
        entity.rolle = benutzer.rolle();
        entity.erstelltAm = benutzer.erstelltAm();
        entity.einwilligungAm = benutzer.einwilligungAm();
        entity.naechstePasskeyNummer = benutzer.naechstePasskeyNummer();
        return entity;
    }

    public Benutzer zuDomain() {
        return new Benutzer(
                new BenutzerId(id),
                Benutzername.ausPersistenz(benutzername),
                rolle,
                erstelltAm,
                einwilligungAm,
                naechstePasskeyNummer
        );
    }
}
