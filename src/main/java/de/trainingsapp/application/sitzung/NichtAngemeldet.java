package de.trainingsapp.application.sitzung;

/** Ein Use-Case verlangt einen angemeldeten Benutzer, es gibt aber keine gueltige Sitzung. */
public class NichtAngemeldet extends RuntimeException {

    public NichtAngemeldet() {
        super("Anmeldung erforderlich");
    }
}
