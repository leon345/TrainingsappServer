package de.trainingsapp.domain.benutzer;

import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import java.time.Instant;
import java.util.Objects;

/**
 * Fachliches Benutzerkonto der Trainingsapp.
 */
public record Benutzer(
        BenutzerId id,
        Benutzername benutzername,
        Rolle rolle,
        Instant erstelltAm,
        Instant einwilligungAm,
        int naechstePasskeyNummer
) {

    public Benutzer {
        Objects.requireNonNull(id, "id darf nicht null sein");
        Objects.requireNonNull(benutzername, "benutzername darf nicht null sein");
        Objects.requireNonNull(rolle, "rolle darf nicht null sein");
        Objects.requireNonNull(erstelltAm, "erstelltAm darf nicht null sein");
        Objects.requireNonNull(einwilligungAm, "einwilligungAm darf nicht null sein");
        if (naechstePasskeyNummer < 1) {
            throw new IllegalArgumentException("naechstePasskeyNummer muss mindestens 1 sein");
        }
    }

    public static Benutzer neuRegistriert(
            BenutzerId id,
            Benutzername benutzername,
            Instant erstelltAm,
            Instant einwilligungAm
    ) {
        return new Benutzer(id, benutzername, Rolle.USER, erstelltAm, einwilligungAm, 1);
    }

    public PasskeyBezeichnung standardPasskeyBezeichnung() {
        return PasskeyBezeichnung.standard(naechstePasskeyNummer);
    }

    public Benutzer mitErhoehterPasskeyNummer() {
        return new Benutzer(
                id,
                benutzername,
                rolle,
                erstelltAm,
                einwilligungAm,
                Math.addExact(naechstePasskeyNummer, 1)
        );
    }
}
