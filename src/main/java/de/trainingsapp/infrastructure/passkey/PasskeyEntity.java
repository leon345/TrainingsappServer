package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "passkey")
public class PasskeyEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "benutzer_id", nullable = false)
    public UUID benutzerId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    public String credentialId;

    @Column(name = "public_key", nullable = false, columnDefinition = "bytea")
    public byte[] publicKey;

    @Column(name = "public_key_algorithm", nullable = false)
    public long publicKeyAlgorithm;

    @Column(name = "sign_count", nullable = false)
    public long signCount;

    @Column(name = "aaguid", nullable = false)
    public UUID aaguid;

    @Column(name = "bezeichnung", nullable = false, length = 50)
    public String bezeichnung;

    @Column(name = "erstellt_am", nullable = false)
    public LocalDate erstelltAm;

    @Column(name = "zuletzt_verwendet_am")
    public LocalDate zuletztVerwendetAm;

    public static PasskeyEntity ausDomain(Passkey passkey) {
        PasskeyEntity entity = new PasskeyEntity();
        entity.id = passkey.id().wert();
        entity.benutzerId = passkey.benutzerId().wert();
        entity.credentialId = passkey.credentialId();
        entity.publicKey = passkey.publicKey();
        entity.publicKeyAlgorithm = passkey.publicKeyAlgorithm();
        entity.signCount = passkey.signCount();
        entity.aaguid = passkey.aaguid();
        entity.bezeichnung = passkey.bezeichnung().wert();
        entity.erstelltAm = passkey.erstelltAm();
        entity.zuletztVerwendetAm = passkey.zuletztVerwendetAm();
        return entity;
    }

    public Passkey zuDomain() {
        return new Passkey(
                new PasskeyId(id),
                new BenutzerId(benutzerId),
                credentialId,
                publicKey,
                publicKeyAlgorithm,
                signCount,
                aaguid,
                new PasskeyBezeichnung(bezeichnung),
                erstelltAm,
                zuletztVerwendetAm
        );
    }
}
