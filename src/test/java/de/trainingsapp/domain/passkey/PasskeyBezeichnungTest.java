package de.trainingsapp.domain.passkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.trainingsapp.domain.DomainException;
import org.junit.jupiter.api.Test;

class PasskeyBezeichnungTest {

    @Test
    void trimmtUndNormalisiertNfc() {
        String nfd = "  Büro Schlüssel  ".replace("ü", "u\u0308");
        PasskeyBezeichnung bezeichnung = new PasskeyBezeichnung(nfd);
        assertEquals("Büro Schlüssel", bezeichnung.wert());
    }

    @Test
    void standardBezeichnungErzeugtPasskeyNummer() {
        assertEquals("Passkey 1", PasskeyBezeichnung.standard(1).wert());
        assertEquals("Passkey 42", PasskeyBezeichnung.standard(42).wert());
    }

    @Test
    void lehntLeereZuLangeUndSteuerOderFormatzeichenAb() {
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung(null));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("   "));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("a".repeat(51)));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("Mein\nPasskey"));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("Mein\u0007Passkey"));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("Mein\u200BPasskey"));
        assertThrows(DomainException.class, () -> new PasskeyBezeichnung("Mein\u202EPasskey"));
    }

    @Test
    void erlaubtGenau50Zeichen() {
        String genau50 = "a".repeat(50);
        assertEquals(genau50, new PasskeyBezeichnung(genau50).wert());
    }
}
