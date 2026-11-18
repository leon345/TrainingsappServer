package de.trainingsapp.infrastructure.passkey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * B4 (Unit): Der gespeicherte oeffentliche Schluessel (X.509) wird fuer jeden Algorithmus wieder
 * korrekt in den COSE-Schluessel der Extension uebersetzt, den webauthn4j bei der Anmeldung prueft.
 */
class PasskeyRecordAlgorithmenTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({"ES256, -7, EC", "RS256, -257, RSA", "EdDSA, -8, Ed25519"})
    void schluesselUndAlgorithmusUeberlebenDieUebersetzung(String name, long cose, String schluesseltyp)
            throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance(schluesseltyp);
        if ("EC".equals(schluesseltyp)) {
            g.initialize(new ECGenParameterSpec("secp256r1"));
        } else if ("RSA".equals(schluesseltyp)) {
            g.initialize(2048);
        }
        KeyPair paar = g.generateKeyPair();
        Passkey passkey = new Passkey(new PasskeyId("Y3JlZA"), BenutzerId.neu(), PasskeyBezeichnung.automatisch(1),
                paar.getPublic().getEncoded(), cose, 0, LocalDate.parse("2026-11-18"), null);

        WebAuthnCredentialRecord record = PasskeyWebAuthnUserProvider.zuRecord(passkey, "x");

        assertEquals(cose, record.getAttestedCredentialData().getCOSEKey().getAlgorithm().getValue(), name);
        assertArrayEquals(paar.getPublic().getEncoded(),
                record.getAttestedCredentialData().getCOSEKey().getPublicKey().getEncoded(), name);
        assertEquals(0, record.getCounter());
        assertEquals("Y3JlZA", record.getCredentialID());
    }

    /**
     * Befund (E13-Muster): {@code fromRequiredPersistedData} der Extension scheitert fuer EdDSA, weil
     * webauthn4j den Algorithmus per Identitaet vergleicht. Deshalb baut {@code PasskeyWebAuthnUserProvider}
     * den Datensatz fuer EdDSA selbst. Schlaegt dieser Test nach einem Update fehl, ist der Fehler behoben
     * und die Umgehung kann entfallen.
     */
    @org.junit.jupiter.api.Test
    void befundExtensionKannEdDsaNichtLaden() throws Exception {
        KeyPair paar = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var daten = new WebAuthnCredentialRecord.RequiredPersistedData("x", "Y3JlZA", new java.util.UUID(0, 0),
                paar.getPublic().getEncoded(), -8, 0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> WebAuthnCredentialRecord.fromRequiredPersistedData(daten));
    }
}
