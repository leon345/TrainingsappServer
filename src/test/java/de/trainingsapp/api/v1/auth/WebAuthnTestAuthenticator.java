package de.trainingsapp.api.v1.auth;

import com.webauthn4j.converter.util.CborConverter;
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
import com.webauthn4j.util.Base64UrlUtil;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kryptografischer Software-Authenticator für realistische WebAuthn-Tests (ES256, RS256, EdDSA, SignCount 0 oder inkrementierend).
 */
public final class WebAuthnTestAuthenticator {

    public enum Algorithmus {
        ES256,
        RS256,
        EDDSA
    }

    private static final ObjectConverter OBJECT_CONVERTER = new ObjectConverter();
    private static final CborConverter CBOR_CONVERTER = OBJECT_CONVERTER.getCborConverter();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Algorithmus algorithmus;
    private final KeyPair keyPair;
    private final COSEKey coseKey;
    private final byte[] credentialIdBytes;
    private final String credentialId;
    private final UUID aaguid;
    private final boolean konstanteNullZaehler;
    private long signCount;

    private WebAuthnTestAuthenticator(Algorithmus algorithmus, boolean konstanteNullZaehler) {
        this.algorithmus = algorithmus;
        this.konstanteNullZaehler = konstanteNullZaehler;
        this.signCount = 0L;
        this.aaguid = new UUID(0L, 0L);
        this.credentialIdBytes = new byte[32];
        RANDOM.nextBytes(this.credentialIdBytes);
        this.credentialId = Base64UrlUtil.encodeToString(this.credentialIdBytes);
        try {
            switch (algorithmus) {
                case ES256 -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                    kpg.initialize(new ECGenParameterSpec("secp256r1"));
                    this.keyPair = kpg.generateKeyPair();
                    this.coseKey = EC2COSEKey.create((ECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.ES256);
                }
                case RS256 -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                    kpg.initialize(2048);
                    this.keyPair = kpg.generateKeyPair();
                    this.coseKey = RSACOSEKey.create((RSAPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.RS256);
                }
                case EDDSA -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
                    this.keyPair = kpg.generateKeyPair();
                    this.coseKey = EdDSACOSEKey.create((EdECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.EdDSA);
                }
                default -> throw new IllegalStateException("Unbekannter Algorithmus: " + algorithmus);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Test-Schlüsselpaar konnte nicht erzeugt werden", e);
        }
    }

    public static WebAuthnTestAuthenticator erstellen(Algorithmus algorithmus, boolean konstanteNullZaehler) {
        return new WebAuthnTestAuthenticator(algorithmus, konstanteNullZaehler);
    }

    public String credentialId() {
        return credentialId;
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public long coseAlgorithmId() {
        return coseKey.getAlgorithm().getValue();
    }

    public Map<String, Object> erstelleRegistrierungsCredential(String challenge, String origin, String rpId) {
        try {
            String clientDataJson = "{\"type\":\"webauthn.create\",\"challenge\":\"" + challenge
                    + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
            byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);

            byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
            byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV | AuthenticatorData.BIT_AT);
            AttestedCredentialData attestedData = new AttestedCredentialData(
                    new AAGUID(aaguid),
                    credentialIdBytes,
                    coseKey
            );
            AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authData =
                    new AuthenticatorData<>(rpIdHash, flags, signCount, attestedData);
            AttestationObject attestationObject = new AttestationObject(authData, new NoneAttestationStatement());
            byte[] attestationBytes = CBOR_CONVERTER.writeValueAsBytes(attestationObject);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("clientDataJSON", Base64UrlUtil.encodeToString(clientDataBytes));
            response.put("attestationObject", Base64UrlUtil.encodeToString(attestationBytes));

            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("id", credentialId);
            credential.put("rawId", credentialId);
            credential.put("type", "public-key");
            credential.put("response", response);
            return credential;
        } catch (Exception e) {
            throw new IllegalStateException("Registrierungs-Credential konnte nicht erzeugt werden", e);
        }
    }

    public Map<String, Object> erstelleAnmeldeCredential(
            String challenge,
            String origin,
            String rpId,
            String optionalesUserHandleBase64Url
    ) {
        try {
            if (!konstanteNullZaehler) {
                signCount++;
            }
            String clientDataJson = "{\"type\":\"webauthn.get\",\"challenge\":\"" + challenge
                    + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
            byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
            byte[] clientDataHash = sha256(clientDataBytes);

            byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
            byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV);
            AuthenticatorData<AuthenticationExtensionAuthenticatorOutput> authData =
                    new AuthenticatorData<>(rpIdHash, flags, signCount);
            byte[] authDataBytes = new com.webauthn4j.converter.AuthenticatorDataConverter(OBJECT_CONVERTER)
                    .convert(authData);

            byte[] zuSignieren = new byte[authDataBytes.length + clientDataHash.length];
            System.arraycopy(authDataBytes, 0, zuSignieren, 0, authDataBytes.length);
            System.arraycopy(clientDataHash, 0, zuSignieren, authDataBytes.length, clientDataHash.length);

            byte[] signatur = signiere(zuSignieren);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("clientDataJSON", Base64UrlUtil.encodeToString(clientDataBytes));
            response.put("authenticatorData", Base64UrlUtil.encodeToString(authDataBytes));
            response.put("signature", Base64UrlUtil.encodeToString(signatur));
            if (optionalesUserHandleBase64Url != null) {
                response.put("userHandle", optionalesUserHandleBase64Url);
            }

            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("id", credentialId);
            credential.put("rawId", credentialId);
            credential.put("type", "public-key");
            credential.put("response", response);
            return credential;
        } catch (Exception e) {
            throw new IllegalStateException("Anmelde-Credential konnte nicht erzeugt werden", e);
        }
    }

    private byte[] signiere(byte[] daten) throws Exception {
        String sigAlg = switch (algorithmus) {
            case ES256 -> "SHA256withECDSA";
            case RS256 -> "SHA256withRSA";
            case EDDSA -> "Ed25519";
        };
        Signature sig = Signature.getInstance(sigAlg);
        sig.initSign(keyPair.getPrivate());
        sig.update(daten);
        return sig.sign();
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
}
