package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regeln fuer Benutzernamen (E28) als Tabelle mit Grenzfaellen. Spalte 2 ist die gespeicherte
 * Form oder FEHLER. Zugleich Pruefung der Regex gegen die beschriebene Absicht.
 */
class BenutzernameTest {

    static final String FEHLER = "FEHLER";

    @ParameterizedTest(name = "[{index}] ''{0}'' -> {1} ({2})")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            # Laenge: 3 und 32 erlaubt, 2 und 33 nicht
            abc                                  | abc                                  | 3 Zeichen
            ab                                   | FEHLER                               | 2 Zeichen
            a                                    | FEHLER                               | 1 Zeichen
            ""                                   | FEHLER                               | leer
            abcdefghijabcdefghijabcdefghijab     | abcdefghijabcdefghijabcdefghijab     | 32 Zeichen
            abcdefghijabcdefghijabcdefghijabc    | FEHLER                               | 33 Zeichen
            a.b                                  | a.b                                  | 3 Zeichen mit Sonderzeichen in der Mitte
            a-b-c-d-e-f-g-h-i-j-k-l-m-n-o-p      | a-b-c-d-e-f-g-h-i-j-k-l-m-n-o-p      | 31 Zeichen, abwechselnd
            # Sonderzeichen am Anfang, am Ende und doppelt
            .abc                                 | FEHLER                               | Punkt am Anfang
            _abc                                 | FEHLER                               | Unterstrich am Anfang
            -abc                                 | FEHLER                               | Bindestrich am Anfang
            abc.                                 | FEHLER                               | Punkt am Ende
            abc_                                 | FEHLER                               | Unterstrich am Ende
            abc-                                 | FEHLER                               | Bindestrich am Ende
            a..b                                 | FEHLER                               | zwei Punkte
            a._b                                 | FEHLER                               | zwei verschiedene Sonderzeichen
            a-_b                                 | FEHLER                               | Bindestrich und Unterstrich
            a.b_c-d                              | a.b_c-d                              | einzelne Sonderzeichen getrennt
            lauf_team.2026                       | lauf_team.2026                       | typisches Pseudonym
            007                                  | 007                                  | nur Ziffern
            # Grossbuchstaben werden zu Kleinbuchstaben
            Anna                                 | anna                                 | Grossbuchstabe am Anfang
            ANNA_B                               | anna_b                               | alles gross
            # Nicht-ASCII, Umlaute
            jürgen                               | FEHLER                               | Umlaut
            JÜRGEN                               | FEHLER                               | Umlaut gross
            straße                               | FEHLER                               | Eszett
            名前名前                               | FEHLER                               | CJK
            anna😀                               | FEHLER                               | Emoji
            # Leerraum wird nicht getrimmt
            " anna"                              | FEHLER                               | Leerzeichen am Anfang
            "anna "                              | FEHLER                               | Leerzeichen am Ende
            "an na"                              | FEHLER                               | Leerzeichen in der Mitte
            "   "                                | FEHLER                               | nur Leerzeichen
            # E-Mail-Adressen und andere Zeichen
            anna@example.org                     | FEHLER                               | E-Mail-Adresse
            anna+b                               | FEHLER                               | Plus
            anna/b                               | FEHLER                               | Schraegstrich
            """)
    void regelnAlsTabelle(String eingabe, String erwartet, String fall) {
        if (FEHLER.equals(erwartet)) {
            assertThrows(UngueltigerBenutzername.class, () -> Benutzername.aus(eingabe), fall);
        } else {
            assertEquals(erwartet, Benutzername.aus(eingabe).wert(), fall);
        }
    }

    @Test
    void steuerzeichenUndTabulatorSindUngueltig() {
        for (String eingabe : new String[] {"anna\t", "an\nna", "an\u0000na", "an\u200Bna", "an\u202Ena", "\tanna"}) {
            assertThrows(UngueltigerBenutzername.class, () -> Benutzername.aus(eingabe), eingabe);
        }
    }

    @Test
    void fehlendeEingabeIstUngueltig() {
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.aus(null));
    }

    @Test
    void gespeicherteFormMussKleingeschriebenSein() {
        assertThrows(UngueltigerBenutzername.class, () -> new Benutzername("Anna"));
        assertEquals("anna", new Benutzername("anna").wert());
    }

    /**
     * Befund zur Regel "Umwandlung vor der Regex" (an den Council gemeldet): Einzelne Nicht-ASCII-
     * Zeichen werden durch {@code toLowerCase(Locale.ROOT)} zu ASCII und dann angenommen, z. B.
     * das Kelvin-Zeichen U+212A zu "k". Gespeichert wird trotzdem nur ASCII.
     */
    @Test
    void kelvinZeichenWirdZuAsciiK() {
        assertEquals("kai", Benutzername.aus("\u212Aai").wert());
        // Dagegen ergibt das grosse I mit Punkt (U+0130) "i" plus kombinierenden Punkt: ungueltig
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.aus("\u0130na"));
    }
}
