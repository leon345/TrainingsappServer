package de.trainingsapp.domain.passkey;

import java.text.Normalizer;
import java.util.Objects;

/**
 * Value Object: sichtbare Bezeichnung eines Passkeys (E29). Unicode ist erlaubt.
 * Die Eingabe wird nach NFC normalisiert und an beiden Enden von Leerraum befreit
 * ({@link String#strip()}); danach 1 bis 50 Zeichen (Code Points), ohne Steuer- oder
 * Formatzeichen (Unicode-Kategorien Cc und Cf) und ohne einzelne Surrogate.
 */
public record PasskeyBezeichnung(String wert) {

    public static final int MAX_LAENGE = 50;

    /** Prueft die gespeicherte (bereits normalisierte) Form. */
    public PasskeyBezeichnung {
        Objects.requireNonNull(wert, "wert");
        if (!Normalizer.isNormalized(wert, Normalizer.Form.NFC) || !wert.equals(wert.strip())) {
            throw new UngueltigeBezeichnung("Bezeichnung ist nicht normalisiert");
        }
        int laenge = wert.codePointCount(0, wert.length());
        if (laenge < 1 || laenge > MAX_LAENGE) {
            throw new UngueltigeBezeichnung("Bezeichnung muss 1 bis " + MAX_LAENGE + " Zeichen lang sein");
        }
        if (wert.codePoints().anyMatch(PasskeyBezeichnung::verboten)) {
            throw new UngueltigeBezeichnung("Bezeichnung enthaelt Steuer- oder Formatzeichen");
        }
    }

    /**
     * Normalisiert eine Benutzereingabe (NFC, Trim) und prueft sie.
     *
     * @throws UngueltigeBezeichnung bei fehlender oder ungueltiger Eingabe
     */
    public static PasskeyBezeichnung aus(String eingabe) {
        if (eingabe == null) {
            throw new UngueltigeBezeichnung("Bezeichnung fehlt");
        }
        return new PasskeyBezeichnung(Normalizer.normalize(eingabe, Normalizer.Form.NFC).strip());
    }

    /** Automatische Bezeichnung "Passkey n" (E29). */
    public static PasskeyBezeichnung automatisch(int nummer) {
        if (nummer < 1) {
            throw new IllegalArgumentException("Nummer muss positiv sein");
        }
        return new PasskeyBezeichnung("Passkey " + nummer);
    }

    private static boolean verboten(int cp) {
        int typ = Character.getType(cp);
        return typ == Character.CONTROL || typ == Character.FORMAT || typ == Character.SURROGATE;
    }

    @Override
    public String toString() {
        return wert;
    }
}
