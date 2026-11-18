package de.trainingsapp.infrastructure.passkey;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import java.util.UUID;

/** Uebersetzt zwischen {@link Passkey} und {@link PasskeyEntity}. */
final class PasskeyMapper {

    private PasskeyMapper() {
    }

    static Passkey zuDomain(PasskeyEntity entity) {
        return new Passkey(new PasskeyId(entity.credentialId), new BenutzerId(entity.benutzerId),
                new PasskeyBezeichnung(entity.bezeichnung), entity.publicKey, entity.algorithmus, entity.zaehler,
                entity.erstelltAm, entity.zuletztVerwendetAm);
    }

    /** Neue Entity mit frisch vergebenem technischem Schluessel. */
    static PasskeyEntity zuNeuerEntity(Passkey passkey) {
        PasskeyEntity entity = new PasskeyEntity();
        entity.id = UUID.randomUUID();
        entity.credentialId = passkey.id().wert();
        entity.benutzerId = passkey.benutzerId().wert();
        entity.bezeichnung = passkey.bezeichnung().wert();
        entity.publicKey = passkey.publicKey();
        entity.algorithmus = passkey.algorithmus();
        entity.zaehler = passkey.zaehler();
        entity.erstelltAm = passkey.erstelltAm();
        entity.zuletztVerwendetAm = passkey.zuletztVerwendetAm();
        return entity;
    }
}
