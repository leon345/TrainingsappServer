package de.trainingsapp.domain.benutzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BenutzernameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "Max.Muster",
            "a_b-c.1",
            "user123",
            "a1234567890123456789012345678901" // 32 Zeichen
    })
    void gueltigeBenutzernamenWerdenAufKleinschreibungNormalisiert(String eingabe) {
        Benutzername name = Benutzername.erstellen(eingabe);
        assertEquals(eingabe.toLowerCase(java.util.Locale.ROOT), name.wert());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "ab",
            "a12345678901234567890123456789012", // 33 Zeichen
            " abc",
            "abc ",
            "ab c",
            ".abc",
            "abc.",
            "-abc",
            "abc_",
            "ab..cd",
            "ab._cd",
            "ab--cd",
            "müller",
            "äbc",
            "user@example"
    })
    void ungueltigeBenutzernamenWerdenAbgelehnt(String eingabe) {
        UngueltigerBenutzernameException ex = assertThrows(
                UngueltigerBenutzernameException.class,
                () -> Benutzername.erstellen(eingabe)
        );
        assertEquals(UngueltigerBenutzernameException.CODE, ex.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "root", "system", "support", "trainingsapp"})
    void standardSperrlisteWirdDurchgesetzt(String gesperrt) {
        assertThrows(UngueltigerBenutzernameException.class, () -> Benutzername.erstellen(gesperrt));
    }

    @Test
    void konfigurierbareSperrlisteWirdZusaetzlichGeprueft() {
        Set<String> extra = Set.of("moderator", "Team-Intern");
        assertThrows(UngueltigerBenutzernameException.class, () -> Benutzername.erstellen("Moderator", extra));
        assertThrows(UngueltigerBenutzernameException.class, () -> Benutzername.erstellen("team-intern", extra));
        assertEquals("athlet1", Benutzername.erstellen("Athlet1", extra).wert());
    }

    @Test
    void nullWirdAbgelehnt() {
        assertThrows(UngueltigerBenutzernameException.class, () -> Benutzername.erstellen(null));
    }
}
