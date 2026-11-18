package de.trainingsapp.domain.passkey;

/** Eine Passkey-Bezeichnung verstoesst gegen die Regeln aus {@link PasskeyBezeichnung}. */
public class UngueltigeBezeichnung extends IllegalArgumentException {

    public UngueltigeBezeichnung(String message) {
        super(message);
    }
}
