package de.trainingsapp.domain.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Validierung der Passkey-Bezeichnung (E29): NFC, Trim, 1 bis 50 Zeichen, keine Steuer-/Formatzeichen. */
class PasskeyBezeichnungTest {

    static Object[][] faelle() {
        String e = "\uD83D\uDD11"; // Schluessel-Emoji, ein Code Point aus zwei UTF-16-Einheiten
        return new Object[][] {
                {"Mein iPhone", "Mein iPhone"},
                {"  Laptop  ", "Laptop"},
                {"\tTablet\n", "Tablet"},
                {"Schlüssel für Büro", "Schlüssel für Büro"},
                {"Cafe\u0301", "Caf\u00E9"},           // NFD -> NFC
                {"名前のキー", "名前のキー"},
                {"x", "x"},
                {"a".repeat(50), "a".repeat(50)},
                {e.repeat(50), e.repeat(50)},           // 50 Zeichen, 100 UTF-16-Einheiten
                {" " + "b".repeat(50) + " ", "b".repeat(50)},
                {"a".repeat(51), null},
                {e.repeat(51), null},
                {"", null},
                {"   ", null},
                {"\u3000", null},                       // nur ideografischer Leerraum
                {"Bell\u0007", null},                   // Steuerzeichen (Cc)
                {"Zeile\neins", null},                  // Zeilenumbruch innen (Cc)
                {"Tab\tinnen", null},
                {"Null\u0000", null},
                {"Zero\u200Bwidth", null},              // Formatzeichen (Cf)
                {"Rechts\u202Elinks", null},            // Richtungsumkehr (Cf)
                {"Soft\u00ADhyphen", null},             // weiches Trennzeichen (Cf)
                {"Halb\uD800", null},                   // einzelnes Surrogat
        };
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' -> {1}")
    @MethodSource("faelle")
    void validierung(String eingabe, String erwartet) {
        if (erwartet == null) {
            assertThrows(UngueltigeBezeichnung.class, () -> PasskeyBezeichnung.aus(eingabe));
        } else {
            assertEquals(erwartet, PasskeyBezeichnung.aus(eingabe).wert());
        }
    }

    @Test
    void fehlendeEingabe() {
        assertThrows(UngueltigeBezeichnung.class, () -> PasskeyBezeichnung.aus(null));
    }

    @Test
    void automatischeBezeichnung() {
        assertEquals("Passkey 1", PasskeyBezeichnung.automatisch(1).wert());
        assertEquals("Passkey 12", PasskeyBezeichnung.automatisch(12).wert());
        assertThrows(IllegalArgumentException.class, () -> PasskeyBezeichnung.automatisch(0));
    }
}
