# Authentifizierungs-Architektur & Erweiterbarkeit (Native / Token)

Dieses Dokument beschreibt den Aufbau der Passkey-Authentifizierung im Trainingsapp-Server (`GS-02`), die Entkopplung über den dünnen Auth-Port (`Anmeldesitzung`, Nutzerentscheid A) sowie dokumentierte Plattform-Eigenschaften von Quarkus `3.40.1.2`.

---

## 1. Dünner Auth-Port (`Anmeldesitzung`) & Zeremonie-Port (`PasskeyZeremonie`)

Die Geschäftslogik in `de.trainingsapp.application.passkey.PasskeyService` ist vollständig von `quarkus-security-webauthn`, Vert.x-Cookies und `SecurityIdentity` entkoppelt (durch `ArchitekturTest` / ArchUnit erzwungen):

- **`de.trainingsapp.application.auth.Anmeldesitzung`** (dünner Auth-Port):
  - `Optional<BenutzerId> aktuellerBenutzer()`
  - `void anmelden(BenutzerId benutzerId)`
  - `void beenden()`
- **`de.trainingsapp.application.passkey.PasskeyZeremonie`**:
  - Kapselt WebAuthn-Challenge-Erzeugung und kryptografische Verifikation (`WebAuthnSecurity`).
- **Aktuelle Web-Implementierung (`de.trainingsapp.infrastructure.passkey`):**
  - `QuarkusAnmeldesitzung` liest die `BenutzerId` (unveränderliche UUID) aus `SecurityIdentity` und setzt/löscht das verschlüsselte WebAuthn-Session-Cookie über `WebAuthnSecurity.rememberUser(...)` / `WebAuthnSecurity.logout(...)`.
  - `QuarkusWebAuthnUserProvider` implementiert `WebAuthnUserProvider` und lädt Passkeys sowie die fachliche Rolle (`USER` oder `ADMIN`) aus PostgreSQL.

### Austauschbarkeit für künftige native Apps (iOS / Android)

Um später zusätzlich oder alternativ Bearer-/Refresh-Tokens (z. B. OIDC / SmallRye JWT) für native Apps anzubieten, bleiben `domain.*`, `PasskeyService`, `ZugriffsPolicy`, `ZugriffsPermissionChecker` und die `/v1`-Ressourcen unverändert:

1. **Neuer Infrastruktur-Adapter für `Anmeldesitzung`:**
   - `aktuellerBenutzer()` liest weiterhin die `BenutzerId` aus `SecurityIdentity` (bei JWT aus dem `sub`-Claim).
   - `anmelden(BenutzerId)` stellt signierte Access-/Refresh-Tokens aus (statt `WebAuthnSecurity.rememberUser`).
   - `beenden()` widerruft das Refresh-Token (statt Cookie-Logout).
2. **Kein Umbau der Geschäftslogik:**
   - Der Unit-Test `PasskeyKryptoUndServiceTest.authPortAustauschMitFakeAnmeldesitzungFunktioniertOhneWebAuthnCookies` weist den Betrieb von `PasskeyService` mit einer reinen In-Memory-`FakeAnmeldesitzung` ohne Cookies oder WebAuthn-HTTP-Kontext nach.

---

## 2. RBAC & ABAC

- **RBAC:** Rollen `USER` und `ADMIN` (`de.trainingsapp.domain.benutzer.Rolle`). `ADMIN` ist ausschließlich für Betriebsaufgaben vorgesehen und erhält **keinen** Zugriff auf fremde Trainingsdaten oder fremde Passkeys.
- **ABAC:** Die fachlichen Regeln liegen framework-frei in `de.trainingsapp.domain.zugriff.ZugriffsPolicy`:
  - `darfPasskeyVerwalten(akteur, passkey)` — ausschließlich der Besitzer.
  - `darfTrainingsdatenLesen(akteur, rolle, besitzer, oeffentlich, eingeladen)` — Besitzer, oder (als `USER`) öffentlich bzw. eingeladen; niemals fremder `ADMIN`.
  - `darfTrainingsdatenBearbeiten(akteur, besitzer)` — ausschließlich der Besitzer.
- **Quarkus-Integration:** `ZugriffsPermissionChecker` (`@PermissionChecker("passkey:verwalten")`, `@Blocking`) delegiert an `ZugriffsPolicy` und liefert bei fehlenden sowie fremden Passkeys einheitlich `404 PASSKEY_NICHT_GEFUNDEN` (verhindert ID-Aufzählung).

---

## 3. Bekannte Plattform-Limitierungen in Quarkus `3.40.1.2`

Gemäß der Projektleitregel *„Standard-Packages vor Eigenentwicklung“* werden für die folgenden zwei Upstream-Eigenschaften bewusst keine technischen Workarounds implementiert, sondern dokumentiert:

### 3.1 Fehlendes `HttpOnly`-Flag bei `quarkus-security-webauthn:3.40.1.2`
- **Befund:** `io.quarkus.security.webauthn.WebAuthnRecorder` instanziiert `PersistentLoginManager` in Quarkus `3.40.1.2` fest mit `httpOnlyCookie = false`; `WebAuthnRunTimeConfig` bietet keinen Konfigurationsschalter für `HttpOnly`. Sowohl das Session-Cookie (`quarkus-credential`) als auch das Challenge-Cookie (`_quarkus_webauthn_challenge`) tragen daher kein `HttpOnly`-Attribut.
- **Kompensierende Maßnahmen:**
  - `quarkus.webauthn.cookie-same-site=strict` (CSRF-Schutz).
  - `quarkus.http.proxy.proxy-address-forwarding=true` — hinter dem TLS-terminierenden Reverse Proxy (`X-Forwarded-Proto: https`) setzt Quarkus automatisch das `Secure`-Flag auf beiden Cookies.
  - Strenge Content-Security-Policy (`Content-Security-Policy: default-src 'self'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`.
  - Das Session-Cookie ist AES-GCM-verschlüsselt (`%prod.quarkus.http.auth.session.encryption-key=${SESSION_ENCRYPTION_KEY}`) und enthält ausschließlich die technische `BenutzerId`.
- **Re-Evaluation:** Bei einem späteren Quarkus-Upgrade prüfen, ob `quarkus-security-webauthn` eine `cookie-http-only`-Option oder `httpOnly=true` als Default nachrüstet.

### 3.2 EdDSA-Referenzgleichheits-Bug in `webauthn4j-core:0.30.3.RELEASE`
- **Befund:** `WebAuthnCredentialRecord.fromRequiredPersistedData(...)` erzeugt über `COSEAlgorithmIdentifier.create(-8L)` eine neue Instanz, während `com.webauthn4j.data.attestation.authenticator.EdDSACOSEKey.create(...)` fälschlich per Referenzgleichheit (`alg == COSEAlgorithmIdentifier.EdDSA`) prüft und `IllegalArgumentException: alg must be EdDSA` wirft.
- **Konfiguration:** `quarkus.webauthn.public-key-credential-parameters=ES256,RS256` (ohne EdDSA, kein Eigenbau-Workaround). Alle gängigen Plattform-Authenticatoren und Sicherheitsschlüssel unterstützen ES256 bzw. RS256 vollständig.
- **Re-Evaluation:** Nach einem Upstream-Fix in `webauthn4j` / Quarkus kann `EdDSA` rein über `application.properties` ergänzt werden.

---

## 4. Blockierung von `/q/webauthn/*`

`quarkus-security-webauthn` registriert trotz `enable-login-endpoint=false` und `enable-registration-endpoint=false` die Standard-Routen `/q/webauthn/login-options-challenge`, `/q/webauthn/register-options-challenge`, `/q/webauthn/webauthn.js` und `/q/webauthn/logout`. Der minimale `WebAuthnStandardRoutenBlocker` (`infrastructure.passkey`, freigegebene Option A) blockiert `/q/webauthn` und `/q/webauthn/*` vorab mit `404` (leerer Body, kein `Set-Cookie`), sodass ausschließlich die versionierten `/v1/*`-Endpunkte erreichbar sind.
