package de.trainingsapp.infrastructure.passkey;

import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.util.Base64UrlUtil;
import de.trainingsapp.application.passkey.PasskeyZeremonie;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Benutzername;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnSecurity;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Infrastruktur-Adapter für PasskeyZeremonie auf Basis von Quarkus WebAuthnSecurity.
 */
@RequestScoped
public class QuarkusPasskeyZeremonie implements PasskeyZeremonie {

    private static final Logger LOG = Logger.getLogger(QuarkusPasskeyZeremonie.class);

    private final WebAuthnSecurity webAuthnSecurity;
    private final RoutingContext routingContext;

    public QuarkusPasskeyZeremonie(WebAuthnSecurity webAuthnSecurity, RoutingContext routingContext) {
        this.webAuthnSecurity = webAuthnSecurity;
        this.routingContext = routingContext;
    }

    @Override
    public String erstelleRegistrierungsOptionen(
            BenutzerId benutzerId,
            Benutzername benutzername,
            List<String> ausgeschlosseneCredentialIds
    ) {
        PublicKeyCredentialCreationOptions basis = webAuthnSecurity
                .getRegisterChallenge(benutzerId.alsText(), benutzername.wert(), routingContext)
                .await().indefinitely();

        PublicKeyCredentialUserEntity userEntity = new PublicKeyCredentialUserEntity(
                benutzerId.alsUserHandle(),
                benutzername.wert(),
                benutzername.wert()
        );
        List<PublicKeyCredentialDescriptor> excludeCredentials = ausgeschlosseneCredentialIds == null
                ? List.of()
                : ausgeschlosseneCredentialIds.stream()
                        .map(id -> new PublicKeyCredentialDescriptor(
                                PublicKeyCredentialType.PUBLIC_KEY,
                                Base64UrlUtil.decode(id),
                                null
                        ))
                        .toList();

        PublicKeyCredentialCreationOptions optionen = new PublicKeyCredentialCreationOptions(
                basis.getRp(),
                userEntity,
                basis.getChallenge(),
                basis.getPubKeyCredParams(),
                basis.getTimeout(),
                excludeCredentials,
                basis.getAuthenticatorSelection(),
                basis.getAttestation(),
                basis.getExtensions()
        );
        return webAuthnSecurity.toJsonString(optionen);
    }

    @Override
    public VerifizierterNeuerPasskey verifiziereRegistrierung(
            BenutzerId benutzerId,
            RegistrierungsAntwort antwort
    ) {
        if (antwort == null) {
            throw new DomainException(
                    "PASSKEY_REGISTRIERUNG_FEHLGESCHLAGEN",
                    "Die Passkey-Registrierungsdaten fehlen."
            );
        }
        JsonObject payload = new JsonObject()
                .put("id", antwort.id())
                .put("rawId", antwort.rawId())
                .put("type", antwort.type())
                .put("response", new JsonObject()
                        .put("clientDataJSON", antwort.clientDataJson())
                        .put("attestationObject", antwort.attestationObject()));
        try {
            WebAuthnCredentialRecord record = webAuthnSecurity
                    .register(benutzerId.alsText(), payload, routingContext)
                    .await().indefinitely();
            WebAuthnCredentialRecord.RequiredPersistedData daten = record.getRequiredPersistedData();
            return new VerifizierterNeuerPasskey(
                    daten.credentialId(),
                    daten.publicKey(),
                    daten.publicKeyAlgorithm(),
                    daten.counter(),
                    daten.aaguid()
            );
        } catch (RuntimeException ex) {
            LOG.debugf("Passkey-Registrierungsverifikation fehlgeschlagen (%s)", ex.getClass().getSimpleName());
            throw new DomainException(
                    "PASSKEY_REGISTRIERUNG_FEHLGESCHLAGEN",
                    "Die Passkey-Registrierung konnte nicht verifiziert werden."
            );
        }
    }

    @Override
    public String erstelleAnmeldeOptionen(List<String> erlaubteCredentialIds) {
        PublicKeyCredentialRequestOptions basis = webAuthnSecurity
                .getLoginChallenge("", routingContext)
                .await().indefinitely();

        if (erlaubteCredentialIds == null || erlaubteCredentialIds.isEmpty()) {
            return webAuthnSecurity.toJsonString(basis);
        }

        List<PublicKeyCredentialDescriptor> allowCredentials = erlaubteCredentialIds.stream()
                .map(id -> new PublicKeyCredentialDescriptor(
                        PublicKeyCredentialType.PUBLIC_KEY,
                        Base64UrlUtil.decode(id),
                        null
                ))
                .toList();

        PublicKeyCredentialRequestOptions optionen = new PublicKeyCredentialRequestOptions(
                basis.getChallenge(),
                basis.getTimeout(),
                basis.getRpId(),
                allowCredentials,
                basis.getUserVerification(),
                basis.getExtensions()
        );
        return webAuthnSecurity.toJsonString(optionen);
    }

    @Override
    public VerifizierteAnmeldung verifiziereAnmeldung(AnmeldeAntwort antwort) {
        if (antwort == null) {
            throw new DomainNichtAutorisiertException(
                    "ANMELDUNG_FEHLGESCHLAGEN",
                    "Die Passkey-Anmeldedaten fehlen."
            );
        }
        JsonObject responseObj = new JsonObject()
                .put("clientDataJSON", antwort.clientDataJson())
                .put("authenticatorData", antwort.authenticatorData())
                .put("signature", antwort.signature());
        if (antwort.userHandle() != null && !antwort.userHandle().isBlank()) {
            responseObj.put("userHandle", antwort.userHandle());
        }
        JsonObject payload = new JsonObject()
                .put("id", antwort.id())
                .put("rawId", antwort.rawId())
                .put("type", antwort.type())
                .put("response", responseObj);
        try {
            WebAuthnCredentialRecord record = webAuthnSecurity
                    .login(payload, routingContext)
                    .await().indefinitely();
            return new VerifizierteAnmeldung(
                    BenutzerId.ausText(record.getUsername()),
                    record.getCredentialID(),
                    record.getCounter()
            );
        } catch (RuntimeException ex) {
            LOG.debugf("Passkey-Anmeldeverifikation fehlgeschlagen (%s)", ex.getClass().getSimpleName());
            throw new DomainNichtAutorisiertException(
                    "ANMELDUNG_FEHLGESCHLAGEN",
                    "Die Passkey-Anmeldung konnte nicht verifiziert werden."
            );
        }
    }
}
