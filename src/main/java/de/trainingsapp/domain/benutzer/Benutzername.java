package de.trainingsapp.domain.benutzer;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object: Benutzername (E17, E28). Ein Pseudonym, keine E-Mail-Adresse und kein Klarname.
 *
 * <p>Regeln (E28):
 * <ol>
 *   <li>Die Eingabe wird mit {@code toLowerCase(Locale.ROOT)} umgewandelt und NICHT getrimmt;
 *       Leerraum ist ein Fehler.</li>
 *   <li>Nach der Umwandlung muss sie {@link #MUSTER} entsprechen: 3 bis 32 Zeichen, nur ASCII
 *       {@code a-z}, {@code 0-9} und {@code . _ -}, Anfang und Ende alphanumerisch, keine zwei
 *       Sonderzeichen hintereinander.</li>
 *   <li>Keine Unicode-Normalisierung.</li>
 *   <li>Gesperrte Namen prueft die Registrierung gegen eine Konfigurationsliste.</li>
 *   <li>Gespeichert und angezeigt wird nur die Kleinschreibung ({@link #wert()}).</li>
 * </ol>
 * Dieselbe Regex steht als CHECK an den Tabellen {@code benutzer} und {@code ausstehende_registrierung}.
 */
public record Benutzername(String wert) {

    /** Regex aus E28, angewendet auf die bereits kleingeschriebene Eingabe. */
    public static final String REGEX = "^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$";

    private static final Pattern MUSTER = Pattern.compile(REGEX);

    /** Prueft die gespeicherte (kleingeschriebene) Form, z. B. beim Laden. */
    public Benutzername {
        Objects.requireNonNull(wert, "wert");
        if (!MUSTER.matcher(wert).matches()) {
            throw new UngueltigerBenutzername(
                    "Benutzername: 3 bis 32 Zeichen aus a-z, 0-9 sowie . _ -, Anfang und Ende Buchstabe oder Ziffer, "
                            + "keine zwei Sonderzeichen hintereinander (keine E-Mail-Adresse, kein Klarname)");
        }
    }

    /**
     * Wandelt eine Benutzereingabe um (Kleinschreibung, ohne Trimmen) und prueft sie.
     *
     * @throws UngueltigerBenutzername bei fehlender oder ungueltiger Eingabe
     */
    public static Benutzername aus(String eingabe) {
        if (eingabe == null) {
            throw new UngueltigerBenutzername("Benutzername fehlt");
        }
        return new Benutzername(eingabe.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return wert;
    }
}
