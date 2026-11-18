package de.trainingsapp.infrastructure.passkey;

import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.util.Base64UrlUtil;
import de.trainingsapp.application.passkey.Anmeldesitzung;
import de.trainingsapp.application.passkey.PasskeyPruefungFehlgeschlagen;
import de.trainingsapp.application.passkey.PasskeyZeremonie;
import de.trainingsapp.domain.benutzer.Benutzer;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnSecurity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Adapter der Ports {@link PasskeyZeremonie} und {@link Anmeldesitzung} auf die
 * Quarkus-WebAuthn-Extension. Es wird nur die programmatische API
 * {@link WebAuthnSecurity} genutzt; die Challenge liegt (verschluesselt) im Cookie
 * {@code quarkus.webauthn.challenge-cookie-name}, die Sitzung im Cookie
 * {@code quarkus.webauthn.cookie-name}.
 */
@ApplicationScoped
public class QuarkusWebAuthnZeremonie implements PasskeyZeremonie, Anmeldesitzung {

    private final WebAuthnSecurity security;
    private final CurrentVertxRequest request;
    private final PasskeyRepository passkeys;
    private final VerbrauchteChallenges verbraucht;
    private final String challengeCookie;

    QuarkusWebAuthnZeremonie(WebAuthnSecurity security, CurrentVertxRequest request, PasskeyRepository passkeys,
                             VerbrauchteChallenges verbraucht,
                             @ConfigProperty(name = "quarkus.webauthn.challenge-cookie-name",
                                     defaultValue = "_quarkus_webauthn_challenge") String challengeCookie) {
        this.security = security;
        this.request = request;
        this.passkeys = passkeys;
        this.verbraucht = verbraucht;
        this.challengeCookie = challengeCookie;
    }

    @Override
    public Optionen registrierungsOptionen(Benutzer benutzer, List<PasskeyId> vorhandene) {
        neueChallengeErzwingen();
        PublicKeyCredentialCreationOptions o = security
                .getRegisterChallenge(benutzer.benutzername().wert(), benutzer.benutzername().wert(), ctx())
                .await().indefinitely();
        // Umweg: Die Extension (3.40) setzt user.id bei JEDEM Aufruf auf eine neue Zufalls-UUID
        // und laesst excludeCredentials leer (quarkus#44292). Fuer mehrere Passkeys pro Benutzer
        // ersetzen wir beides: stabiler User-Handle (16 Rohbytes der BenutzerId, E18), vorhandene Passkeys ausschliessen.
        PublicKeyCredentialCreationOptions angepasst = new PublicKeyCredentialCreationOptions(
                o.getRp(),
                new PublicKeyCredentialUserEntity(UserHandle.zuBytes(benutzer.id()), o.getUser().getName(),
                        o.getUser().getDisplayName()),
                o.getChallenge(),
                o.getPubKeyCredParams(),
                o.getTimeout(),
                vorhandene.stream()
                        .map(id -> new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY,
                                Base64UrlUtil.decode(id.wert()), null))
                        .toList(),
                o.getAuthenticatorSelection(),
                o.getAttestation(),
                o.getExtensions());
        return new Optionen(security.toJsonString(angepasst), Base64UrlUtil.encodeToString(o.getChallenge().getValue()));
    }

    @Override
    public NeuerPasskey registrierungPruefen(String antwortJson) {
        WebAuthnCredentialRecord record;
        try {
            // Der "username" wird von register() nur in den Record kopiert; die Zuordnung
            // zum Benutzer macht der PasskeyService.
            record = security.register("-", parse(antwortJson), ctx()).await().indefinitely();
        } catch (RuntimeException e) {
            throw new PasskeyPruefungFehlgeschlagen("Registrierung abgelehnt", e);
        }
        String challenge = challengeAus(antwortJson);
        verbrauche(challenge);
        WebAuthnCredentialRecord.RequiredPersistedData d = record.getRequiredPersistedData();
        return new NeuerPasskey(new PasskeyId(d.credentialId()), d.publicKey(),
                d.publicKeyAlgorithm(), d.counter(), challenge);
    }

    @Override
    public String anmeldeOptionen() {
        neueChallengeErzwingen();
        return security.toJsonString(security.getLoginChallenge(null, ctx()).await().indefinitely());
    }

    @Override
    public Passkey anmeldungPruefen(String antwortJson) {
        WebAuthnCredentialRecord record;
        try {
            record = security.login(parse(antwortJson), ctx()).await().indefinitely();
        } catch (RuntimeException e) {
            throw new PasskeyPruefungFehlgeschlagen("Anmeldung abgelehnt", e);
        }
        // Die Signatur (samt Origin, rpId, UV, Zaehler) ist geprueft: Challenge sofort verbrauchen,
        // damit dieselbe Antwort nicht erneut eingereicht werden kann.
        verbrauche(challengeAus(antwortJson));
        PasskeyId id = new PasskeyId(record.getCredentialID());
        Passkey gespeichert = passkeys.findeNachId(id)
                .orElseThrow(() -> new PasskeyPruefungFehlgeschlagen("Passkey unbekannt"));
        // WebAuthn L3, 7.2 Schritt 6: Ohne allowCredentials (discoverable) muss der User-Handle
        // vorhanden sein und zum Besitzer des Passkeys gehoeren. Die Extension prueft das nicht.
        pruefeUserHandle(antwortJson, gespeichert);
        return gespeichert.mitZaehler(record.getCounter());
    }

    @Override
    public void starte(BenutzerId benutzerId) {
        security.rememberUser(benutzerId.toString(), ctx());
    }

    @Override
    public void beende() {
        security.logout(ctx());
    }

    /**
     * Die Extension uebernimmt eine Challenge aus einem mitgeschickten Challenge-Cookie, statt eine neue
     * zu erzeugen. Damit jede Zeremonie eine eigene, einmal verwendbare Challenge bekommt, wird das
     * eingehende Cookie vor dem Aufruf aus dem Request entfernt (ohne Set-Cookie zu erzeugen).
     */
    private void neueChallengeErzwingen() {
        ctx().response().removeCookie(challengeCookie, false);
    }

    private void verbrauche(String challenge) {
        if (!verbraucht.verbrauche(challenge)) {
            throw new PasskeyPruefungFehlgeschlagen("Challenge wurde bereits verwendet");
        }
    }

    private static void pruefeUserHandle(String antwortJson, Passkey gespeichert) {
        String handle = parse(antwortJson).getJsonObject("response").getString("userHandle");
        if (handle == null || handle.isEmpty()) {
            throw new PasskeyPruefungFehlgeschlagen("User-Handle fehlt");
        }
        byte[] erwartet = UserHandle.zuBytes(gespeichert.benutzerId());
        byte[] geliefert;
        try {
            geliefert = Base64.getUrlDecoder().decode(handle);
        } catch (IllegalArgumentException e) {
            throw new PasskeyPruefungFehlgeschlagen("User-Handle ungueltig", e);
        }
        if (!MessageDigest.isEqual(erwartet, geliefert)) {
            throw new PasskeyPruefungFehlgeschlagen("User-Handle passt nicht zum Besitzer des Passkeys");
        }
    }

    /** Challenge aus clientDataJSON; nach erfolgreicher Pruefung ist sie gleich der Challenge im Cookie. */
    private static String challengeAus(String antwortJson) {
        try {
            String clientData = parse(antwortJson).getJsonObject("response").getString("clientDataJSON");
            return new JsonObject(new String(Base64.getUrlDecoder().decode(clientData), StandardCharsets.UTF_8))
                    .getString("challenge");
        } catch (RuntimeException e) {
            throw new PasskeyPruefungFehlgeschlagen("clientDataJSON ungueltig", e);
        }
    }

    private RoutingContext ctx() {
        return request.getCurrent();
    }

    private static JsonObject parse(String json) {
        try {
            return new JsonObject(json);
        } catch (DecodeException | NullPointerException e) {
            throw new PasskeyPruefungFehlgeschlagen("Antwort ist kein gueltiges JSON", e);
        }
    }
}
