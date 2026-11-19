package de.trainingsapp.domain.passkey;

import de.trainingsapp.domain.DomainException;
import java.text.Normalizer;

/**
 * Value Object für die nutzerfreundliche Bezeichnung eines Passkeys (Trim, NFC, 1–50 Zeichen, keine Steuer-/Formatzeichen).
 */
public record PasskeyBezeichnung(String wert) {

    public static final String FEHLERCODE = "UNGUELTIGE_PASSKEY_BEZEICHNUNG";
    public static final int MAX_LAENGE = 50;

    public PasskeyBezeichnung {
        if (wert == null) {
            throw new DomainException(FEHLERCODE, "Die Passkey-Bezeichnung darf nicht leer sein.");
        }
        String normalisiert = Normalizer.normalize(wert, Normalizer.Form.NFC).trim();
        if (normalisiert.isEmpty() || normalisiert.length() > MAX_LAENGE) {
            throw new DomainException(
                    FEHLERCODE,
                    "Die Passkey-Bezeichnung muss zwischen 1 und 50 Zeichen lang sein."
            );
        }
        for (int i = 0; i < normalisiert.length(); ) {
            int codePoint = normalisiert.codePointAt(i);
            int typ = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || typ == Character.CONTROL
                    || typ == Character.FORMAT
                    || typ == Character.LINE_SEPARATOR
                    || typ == Character.PARAGRAPH_SEPARATOR) {
                throw new DomainException(
                        FEHLERCODE,
                        "Die Passkey-Bezeichnung darf keine Steuer- oder Formatzeichen enthalten."
                );
            }
            i += Character.charCount(codePoint);
        }
        wert = normalisiert;
    }

    public static PasskeyBezeichnung standard(int nummer) {
        if (nummer < 1) {
            throw new IllegalArgumentException("Passkey-Nummer muss mindestens 1 sein.");
        }
        return new PasskeyBezeichnung("Passkey " + nummer);
    }
}
