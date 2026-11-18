package de.trainingsapp.testhilfe;

import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.authenticator.EdDSACOSEKey;
import com.webauthn4j.data.attestation.authenticator.RSACOSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Software-Authenticator fuer Tests: erzeugt Registrierungs- und Anmeldeantworten im JSON-Format von
 * {@code PublicKeyCredential.toJSON()} (Attestation "none"). Anders als {@code WebAuthnHardware} der
 * Extension unterstuetzt er ES256, RS256 und EdDSA, liefert den User-Handle bei der Anmeldung mit und
 * erlaubt gezielte Abweichungen (fremder User-Handle, anderer Origin oder rpId, Zaehler immer 0).
 */
public final class TestAuthenticator {

    public enum Algorithmus {
        ES256(COSEAlgorithmIdentifier.ES256, "SHA256withECDSA"),
        RS256(COSEAlgorithmIdentifier.RS256, "SHA256withRSA"),
        EDDSA(COSEAlgorithmIdentifier.EdDSA, "Ed25519");

        final COSEAlgorithmIdentifier cose;
        final String signatur;

        Algorithmus(COSEAlgorithmIdentifier cose, String signatur) {
            this.cose = cose;
            this.signatur = signatur;
        }
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ObjectConverter KONVERTER = new ObjectConverter();

    private final Algorithmus algorithmus;
    private final KeyPair schluessel;
    private final byte[] credentialId = new byte[32];
    private String origin;
    private String rpId;
    private long zaehler;
    private boolean zaehlerImmerNull;
    private byte[] userHandle;
    private byte[] userHandleUeberschrieben;
    private boolean ohneUserHandle;

    public TestAuthenticator(URL server) {
        this(server, Algorithmus.ES256);
    }

    public TestAuthenticator(URL server, Algorithmus algorithmus) {
        this.algorithmus = algorithmus;
        // Wie ein Browser auf der Seite des Servers: Origin und rpId aus der Allowlist des Testprofils
        this.origin = TestOrigin.wert();
        this.rpId = TestOrigin.host();
        this.schluessel = erzeugeSchluessel(algorithmus);
        new SecureRandom().nextBytes(credentialId);
    }

    public TestAuthenticator zaehlerImmerNull() {
        this.zaehlerImmerNull = true;
        return this;
    }

    public TestAuthenticator mitOrigin(String anderer) {
        this.origin = anderer;
        return this;
    }

    public TestAuthenticator mitRpId(String anderer) {
        this.rpId = anderer;
        return this;
    }

    /** Liefert bei der Anmeldung einen anderen User-Handle als bei der Registrierung gespeichert. */
    public TestAuthenticator mitUserHandle(byte[] anderer) {
        this.userHandleUeberschrieben = anderer.clone();
        return this;
    }

    public TestAuthenticator ohneUserHandle() {
        this.ohneUserHandle = true;
        return this;
    }

    public String credentialId() {
        return B64.encodeToString(credentialId);
    }

    public byte[] userHandle() {
        return userHandle == null ? null : userHandle.clone();
    }

    /** Antwort auf PublicKeyCredentialCreationOptions (wie navigator.credentials.create().toJSON()). */
    public JsonObject registriere(JsonObject optionen) {
        userHandle = B64D.decode(optionen.getJsonObject("user").getString("id"));
        byte[] clientData = clientData("webauthn.create", optionen.getString("challenge"));
        AttestedCredentialData attestiert = new AttestedCredentialData(AAGUID.ZERO, credentialId, coseKey());
        AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authData = new AuthenticatorData<>(
                rpIdHash(), (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV | AuthenticatorData.BIT_AT),
                naechsterZaehler(), attestiert);
        byte[] attestation = new AttestationObjectConverter(KONVERTER)
                .convertToBytes(new AttestationObject(authData, new NoneAttestationStatement()));
        return new JsonObject()
                .put("id", credentialId())
                .put("rawId", credentialId())
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform")
                .put("clientExtensionResults", new JsonObject())
                .put("response", new JsonObject()
                        .put("clientDataJSON", B64.encodeToString(clientData))
                        .put("attestationObject", B64.encodeToString(attestation))
                        .put("transports", new io.vertx.core.json.JsonArray().add("internal")));
    }

    /** Antwort auf PublicKeyCredentialRequestOptions (wie navigator.credentials.get().toJSON()). */
    public JsonObject meldeAn(JsonObject optionen) {
        byte[] clientData = clientData("webauthn.get", optionen.getString("challenge"));
        AuthenticatorData<AuthenticationExtensionAuthenticatorOutput> authData = new AuthenticatorData<>(
                rpIdHash(), (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV), naechsterZaehler());
        byte[] authBytes = new AuthenticatorDataConverter(KONVERTER).convert(authData);
        byte[] signatur = signiere(authBytes, sha256(clientData));
        JsonObject antwort = new JsonObject()
                .put("clientDataJSON", B64.encodeToString(clientData))
                .put("authenticatorData", B64.encodeToString(authBytes))
                .put("signature", B64.encodeToString(signatur));
        byte[] handle = userHandleUeberschrieben != null ? userHandleUeberschrieben : userHandle;
        if (!ohneUserHandle && handle != null) {
            antwort.put("userHandle", B64.encodeToString(handle));
        }
        return new JsonObject()
                .put("id", credentialId())
                .put("rawId", credentialId())
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform")
                .put("clientExtensionResults", new JsonObject())
                .put("response", antwort);
    }

    private long naechsterZaehler() {
        return zaehlerImmerNull ? 0 : ++zaehler;
    }

    private byte[] clientData(String typ, String challenge) {
        return new JsonObject().put("type", typ).put("challenge", challenge).put("origin", origin)
                .put("crossOrigin", false).encode().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] rpIdHash() {
        return sha256(rpId.getBytes(StandardCharsets.UTF_8));
    }

    private COSEKey coseKey() {
        return switch (algorithmus) {
            case ES256 -> EC2COSEKey.create((ECPublicKey) schluessel.getPublic(), algorithmus.cose);
            case RS256 -> RSACOSEKey.create((RSAPublicKey) schluessel.getPublic(), algorithmus.cose);
            case EDDSA -> EdDSACOSEKey.create((EdECPublicKey) schluessel.getPublic(), algorithmus.cose);
        };
    }

    private byte[] signiere(byte[] authData, byte[] clientDataHash) {
        try {
            Signature s = Signature.getInstance(algorithmus.signatur);
            s.initSign(schluessel.getPrivate());
            s.update(authData);
            s.update(clientDataHash);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair erzeugeSchluessel(Algorithmus algorithmus) {
        try {
            return switch (algorithmus) {
                case ES256 -> {
                    KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                    g.initialize(new ECGenParameterSpec("secp256r1"));
                    yield g.generateKeyPair();
                }
                case RS256 -> {
                    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                    g.initialize(2048);
                    yield g.generateKeyPair();
                }
                case EDDSA -> KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            };
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] daten) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(daten);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
