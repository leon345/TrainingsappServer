package de.trainingsapp.domain.benutzer;

/** Ein neuer Benutzername verstoesst gegen die Regeln aus {@link Benutzername}. */
public class UngueltigerBenutzername extends IllegalArgumentException {

    public UngueltigerBenutzername(String message) {
        super(message);
    }
}
