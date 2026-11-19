package de.trainingsapp.domain.benutzer;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Value Object für Benutzernamen: ASCII-Kleinschreibung, 3–32 Zeichen, kein Trim, Sperrliste.
 */
public record Benutzername(String wert) {

    public static final String REGEX = "^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$";
    public static final Set<String> STANDARD_SPERRLISTE = Set.of(
            "admin",
            "root",
            "system",
            "support",
            "trainingsapp"
    );

    private static final Pattern MUSTER = Pattern.compile(REGEX);

    public Benutzername {
        if (wert == null) {
            throw new UngueltigerBenutzernameException("Der Benutzername darf nicht leer sein.");
        }
        String normalisiert = wert.toLowerCase(Locale.ROOT);
        if (!MUSTER.matcher(normalisiert).matches()) {
            throw new UngueltigerBenutzernameException(
                    "Der Benutzername muss 3 bis 32 Zeichen lang sein, nur ASCII-Buchstaben, Ziffern sowie '.', '_' oder '-' (nicht am Anfang, Ende oder doppelt) enthalten und darf keine Leerzeichen enthalten."
            );
        }
        wert = normalisiert;
    }

    /**
     * Erstellt einen Benutzernamen und prüft gegen Standard- und konfigurierte Sperrliste.
     */
    public static Benutzername erstellen(String eingabe, Collection<String> zusaetzlicheSperrliste) {
        Benutzername benutzername = new Benutzername(eingabe);
        if (STANDARD_SPERRLISTE.contains(benutzername.wert)) {
            throw new UngueltigerBenutzernameException("Dieser Benutzername ist reserviert.");
        }
        if (zusaetzlicheSperrliste != null) {
            for (String gesperrt : zusaetzlicheSperrliste) {
                if (gesperrt != null && benutzername.wert.equals(gesperrt.trim().toLowerCase(Locale.ROOT))) {
                    throw new UngueltigerBenutzernameException("Dieser Benutzername ist reserviert.");
                }
            }
        }
        return benutzername;
    }

    /**
     * Erstellt einen Benutzernamen mit Prüfung gegen die Standard-Sperrliste.
     */
    public static Benutzername erstellen(String eingabe) {
        return erstellen(eingabe, Set.of());
    }

    /**
     * Stellt einen bereits gespeicherten Benutzernamen aus der Datenbank wieder her.
     */
    public static Benutzername ausPersistenz(String gespeicherterWert) {
        return new Benutzername(gespeicherterWert);
    }
}
