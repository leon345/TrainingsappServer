package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BenutzernameTest {

    @ParameterizedTest
    @ValueSource(strings = {"anna", "Anna B", "läufer_42", "x", "Ölmühle-Team", "名前"})
    void pseudonymeSindErlaubt(String name) {
        assertEquals(name, Benutzername.pruefe(name));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void leerIstUngueltig(String name) {
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.pruefe(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anna@example.org", "@anna", "anna@", "a@b"})
    void klammeraffeIstUngueltig(String name) {
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.pruefe(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {" anna", "anna ", "anna\n", "an\u0000na", "an\u200Bna", "an\u202Ena"})
    void leerraumAmRandUndSteuerzeichenSindUngueltig(String name) {
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.pruefe(name));
    }

    @org.junit.jupiter.api.Test
    void hoechstens64Zeichen() {
        assertEquals("a".repeat(64), Benutzername.pruefe("a".repeat(64)));
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.pruefe("a".repeat(65)));
        // Zeichen, nicht UTF-16-Einheiten: 64 Emoji (je 2 char) sind erlaubt
        String emoji = "\uD83C\uDFCB";
        assertEquals(emoji.repeat(64), Benutzername.pruefe(emoji.repeat(64)));
        assertThrows(UngueltigerBenutzername.class, () -> Benutzername.pruefe(emoji.repeat(65)));
    }
}
