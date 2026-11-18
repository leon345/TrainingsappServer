package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import java.util.UUID;

/** Uebersetzt zwischen {@link Passkey} und {@link PasskeyEntity}. */
final class PasskeyMapper {

    private PasskeyMapper() {
    }

    static Passkey zuDomain(PasskeyEntity entity) {
        return new Passkey(new PasskeyId(entity.credentialId), new BenutzerId(entity.benutzerId), entity.aaguid,
                entity.publicKey, entity.algorithmus, entity.zaehler, entity.erstelltAm);
    }

    /** Neue Entity mit frisch vergebenem technischem Schluessel. */
    static PasskeyEntity zuNeuerEntity(Passkey passkey) {
        PasskeyEntity entity = new PasskeyEntity();
        entity.id = UUID.randomUUID();
        entity.credentialId = passkey.id().wert();
        entity.benutzerId = passkey.benutzerId().wert();
        entity.aaguid = passkey.aaguid();
        entity.publicKey = passkey.publicKey();
        entity.algorithmus = passkey.algorithmus();
        entity.zaehler = passkey.zaehler();
        entity.erstelltAm = passkey.erstelltAm();
        return entity;
    }
}
