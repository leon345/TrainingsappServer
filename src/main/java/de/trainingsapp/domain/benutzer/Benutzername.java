package de.trainingsapp.domain.benutzer;

/**
 * Regeln fuer neue Benutzernamen (E17): Benutzernamen sind Pseudonyme, keine E-Mail-Adressen
 * und keine Klarnamen. Der Name wird dem Authenticator als Kontoname angezeigt und ist fuer
 * andere Benutzer sichtbar (Einladungen).
 *
 * <p>Geprueft wird nur bei der Registrierung, nicht beim Laden: Eine spaetere Verschaerfung
 * der Regeln darf bestehende Konten nicht unlesbar machen.
 *
 * <p>Gross- und Kleinschreibung unterscheidet die Eindeutigkeit nicht ("Anna" und "anna"
 * sind derselbe Name); das setzen die Adapter um.
 */
public final class Benutzername {

    /** Hoechstlaenge in Unicode-Zeichen (Code Points). */
    public static final int MAX_LAENGE = 64;

    private Benutzername() {
    }

    /**
     * @throws UngueltigerBenutzername wenn der Name leer ist, zu lang ist, ein {@code @} enthaelt,
     *                                 mit Leerraum beginnt oder endet oder Steuerzeichen enthaelt
     */
    public static String pruefe(String name) {
        if (name == null || name.isBlank()) {
            throw new UngueltigerBenutzername("Benutzername fehlt");
        }
        if (name.codePointCount(0, name.length()) > MAX_LAENGE) {
            throw new UngueltigerBenutzername("Benutzername ist laenger als " + MAX_LAENGE + " Zeichen");
        }
        if (name.indexOf('@') >= 0) {
            throw new UngueltigerBenutzername("Benutzername darf kein @ enthalten (keine E-Mail-Adresse)");
        }
        if (!name.equals(name.strip())) {
            throw new UngueltigerBenutzername("Benutzername darf nicht mit Leerraum beginnen oder enden");
        }
        if (name.codePoints().anyMatch(Benutzername::istSteuerzeichen)) {
            throw new UngueltigerBenutzername("Benutzername enthaelt Steuerzeichen");
        }
        return name;
    }

    private static boolean istSteuerzeichen(int cp) {
        int typ = Character.getType(cp);
        return Character.isISOControl(cp) || typ == Character.FORMAT || typ == Character.LINE_SEPARATOR
                || typ == Character.PARAGRAPH_SEPARATOR || typ == Character.UNASSIGNED
                || typ == Character.PRIVATE_USE || typ == Character.SURROGATE;
    }
}
