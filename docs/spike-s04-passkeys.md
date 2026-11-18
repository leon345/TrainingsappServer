# Spike S-04: Passkeys mit der Quarkus-WebAuthn-Extension

Stand: 2026-11-18, Branch `spike/S-04-passkeys` (Basis `integration/server` f83267e),
Quarkus 3.40.1.2, `quarkus-security-webauthn` 3.40.1.2 (intern webauthn4j 0.30.3).
Spike nach E3. Das ist **keine** fertige Authentifizierung. Alle Daten liegen nur im Arbeitsspeicher (E9).

## 1. Frage und Antwort

**Frage (E3):** Unterstützt die Quarkus-WebAuthn-Extension mehrere Passkeys pro Benutzer,
einschließlich des Ablaufs, mit dem ein angemeldeter Benutzer einen weiteren Passkey hinzufügt?

**Antwort: Ja, aber nur mit Umweg.** Die eingebauten Endpunkte (`/q/webauthn/...`) unterstützen
den Ablauf nicht. Die programmatische API `WebAuthnSecurity` der Extension unterstützt ihn, wenn
wir eigene Endpunkte unter `/v1` schreiben und zwei Stellen der Registrierungsoptionen selbst
setzen. Der Akzeptanztest läuft damit vollständig grün.

Belege:
- Upstream-Issue [quarkus#44292](https://github.com/quarkusio/quarkus/issues/44292),
  "WebAuthn: support registration and login when already logged in", ist offen. Im Code
  ist es referenziert (`WebAuthnSecurity.getRegisterChallenge`, `if (false)` bei `excludeCredentials`).
- `WebAuthnUserProvider.store()` (Javadoc): Wer mehrere Credentials pro Benutzer will, muss selbst
  sicherstellen, dass der Aufrufer als derselbe Benutzer angemeldet ist. Die Extension prüft das nicht.
- `getRegisterChallenge` setzt `user.id` (User-Handle) bei **jedem** Aufruf auf eine neue
  Zufalls-UUID und lässt `excludeCredentials` leer. Für mehrere Passkeys pro Konto ist das falsch.
  Es führt zu doppelten Einträgen auf demselben Authenticator, und der Benutzer ist nicht
  über den User-Handle erkennbar.
- Datenmodell und Anmeldung sind mehrfähig: `findByUsername` liefert eine Liste,
  `login()` sucht über die Credential-ID (`findByCredentialId`). Belegt durch `PasskeyAkzeptanzTest`.

**Umweg (umgesetzt in `infrastructure.passkey.QuarkusWebAuthnZeremonie`):**
1. Die eingebauten Endpunkte `register` und `login` bleiben aus (Default). Eigene Endpunkte unter `/v1`
   rufen `WebAuthnSecurity.getRegisterChallenge/register/getLoginChallenge/login/rememberUser` auf.
2. Die von der Extension erzeugten `PublicKeyCredentialCreationOptions` werden neu aufgebaut.
   Dabei wird der User-Handle stabil aus der `BenutzerId` abgeleitet, und `excludeCredentials`
   wird mit den vorhandenen Passkeys gefüllt. Das sind etwa 15 Zeilen mit webauthn4j-Typen.
   Die Challenge und das Challenge-Cookie bleiben die der Extension.
3. Der Benutzer zum Hinzufügen kommt **nur aus der Sitzung** (`@Authenticated`, `SecurityIdentity`).
   `register()` bekommt keinen Benutzernamen vom Client. Die Zuordnung macht `PasskeyService`.
4. Die Löschsperre für den letzten Passkey liegt atomar im Repository-Port
   (`loescheWennNichtLetzter`). Die Extension kennt kein Löschen.

Alles liegt hinter eigenen Ports (E3):
- `application.passkey.PasskeyZeremonie`: Optionen erzeugen, Antworten prüfen.
- `application.passkey.Anmeldesitzung`: Sitzung starten und beenden.
- `domain.passkey.PasskeyRepository` und `domain.benutzer.BenutzerRepository`: Ablage.

`api.v1` und `application` kennen weder Vert.x noch die Extension. Ein Wechsel auf webauthn4j
direkt betrifft nur `infrastructure.passkey`.

## 2. Ablauf (Spike-Endpunkte)

| Schritt | Endpunkt | Authentifiziert |
|---|---|---|
| Registrierung starten | `POST /v1/registrieren/optionen` `{benutzername}` → CreationOptions + Challenge-Cookie | nein |
| Registrierung abschließen | `POST /v1/registrieren` `{benutzername, credential}` → 204 + Sitzungs-Cookie | nein |
| Anmeldung starten | `POST /v1/anmelden/optionen` → RequestOptions (ohne Benutzername, discoverable) | nein |
| Anmeldung abschließen | `POST /v1/anmelden` `credential` → 204 + Sitzungs-Cookie | nein |
| Passkeys auflisten | `GET /v1/passkeys` | ja |
| **Weiteren Passkey hinzufügen** | `POST /v1/passkeys/optionen`, dann `POST /v1/passkeys` `credential` → 201 | ja |
| Passkey löschen | `DELETE /v1/passkeys/{id}` → 204, 404 (fremd/unbekannt), 409 `LETZTER_PASSKEY` | ja |

Damit bestätigt sich die Arbeitsannahme aus E3: Der zweite Passkey wird im angemeldeten Zustand registriert.

## 3. Nachweis

`mvn package`: **39 Tests, alle grün** (29 bestehende + 10 neue).

`api.v1.PasskeyAkzeptanzTest`. Als Authenticator dient `WebAuthnHardware` aus
`quarkus-test-security-webauthn`, ein Software-Authenticator mit ES256, Attestation `none`
und rpId `localhost`. Jeder `CookieFilter` steht für einen Browser.

| Akzeptanzschritt | Nachweis |
|---|---|
| zwei Passkeys registrieren | ja: erster per `/v1/registrieren`, zweiter angemeldet per `/v1/passkeys`; geprüft wird auch der gleiche User-Handle und `excludeCredentials` mit 1 Eintrag |
| mit jedem anmelden | ja: zwei frische Sitzungen, beide sehen dieselbe Liste |
| auflisten | ja: 2 Einträge |
| einen löschen | ja: 204, danach 1 Eintrag; der gelöschte Passkey kann sich nicht mehr anmelden (400) |
| letzten nicht löschen | ja: 409 `LETZTER_PASSKEY`, Anmeldung mit dem verbliebenen Passkey klappt weiter |

Zusätzlich getestet: Verwaltung ohne Sitzung ergibt 401, fremden Passkey löschen ergibt 404,
doppelter Benutzername ergibt 409, Registrierung ohne Challenge-Cookie ergibt 400.

Befund-Tests (`infrastructure.passkey.WebAuthnExtensionBefundTest`, `WebAuthnQPfadeGesperrtTest`)
halten das Verhalten der Extension fest. Schlagen sie nach einem Quarkus-Update fehl, ist der Befund neu zu prüfen.

## 4. Befund: Pfade (`/q/webauthn` gegen `/v1`, E12)

- Die Extension registriert ihre Routen fest unter dem Non-Application-Root (`/q/`).
  Im Quellcode (`WebAuthnRecorder`) steht dazu `// FIXME: paths configurable`, die Pfade sind also nicht konfigurierbar.
- **Immer aktiv**, solange die Extension aktiv ist, und nicht einzeln abschaltbar:
  `GET /q/webauthn/register-options-challenge`, `GET /q/webauthn/login-options-challenge`,
  `GET /q/webauthn/webauthn.js`, `GET /q/webauthn/logout` sowie `GET /.well-known/webauthn`.
- Abschaltbar (Default aus): `POST /q/webauthn/register` und `POST /q/webauthn/login` (404, getestet).
- Mit "API unter `/v1`" ist das **vereinbar**, weil wir nur die programmatische API nutzen.
  Mit "kein `/q/*` in Prod" ist es **nur mit Sperre** vereinbar. Belegt ist die Variante
  `quarkus.http.auth.permission.<name>.paths=/q/webauthn/*` mit `policy=deny`. Sie sperrt
  diese Pfade, `/v1` bleibt erreichbar. Die Sperre antwortet aber mit **302 auf `/login.html`**
  statt mit 401/403/404. Alternativ sperrt der Reverse Proxy alles außer `/v1` (E12-Annahme).
  Der geplante E12-Test "kein `/q/*` in Prod" muss 302 als "nicht erreichbar" werten oder die Sperre anders lösen.
- `/.well-known/webauthn` (Related Origins) liegt nicht unter `/q`. Bei einer Origin ist es harmlos,
  für eine mögliche App-Origin (Capacitor) eventuell sogar nützlich.

## 5. Befund: Sitzung und Cookies

- Die **Challenge** liegt AES-verschlüsselt im Cookie `_quarkus_webauthn_challenge`. Der Server
  hält dafür keinen Zustand, mehrere Instanzen gehen bei gleichem Schlüssel.
- Die **Sitzung** liegt im verschlüsselten Cookie `quarkus-credential` (`PersistentLoginManager`,
  Inhalt: Principal = `BenutzerId`, Ablaufzeit). Es gibt keine serverseitige Sitzung, also kein
  "alle Sitzungen abmelden" und keinen Widerruf vor Ablauf. Defaults: Leerlauf 30 min
  (`quarkus.webauthn.session-timeout`), Erneuerung nach 1 min, Session-Cookie ohne Max-Age.
- Attribute (getestet): `Path=/; SameSite=Strict`, `Secure` nur bei HTTPS-Request.
  **Nicht `HttpOnly`**: `WebAuthnRecorder` übergibt `httpOnly=false` fest, das ist nicht
  konfigurierbar. Per XSS ist die Sitzung damit stehlbar. Für Prod ist ein Umweg nötig,
  z. B. ein Vert.x-Filter, der das Attribut an `Set-Cookie` anhängt (nicht gebaut), oder ein Upstream-Fix.
- Der **Schlüssel** kommt aus `quarkus.http.auth.session.encryption-key`. Fehlt er, erzeugt
  die Extension einen Zufallsschlüssel und **schreibt ihn mit WARN ins Log**. Nach einem
  Neustart sind dann alle Sitzungen weg. In Prod also Pflicht per Umgebungsvariable.
- Ohne Anmeldung antwortet der Mechanismus der Extension mit **302 auf `quarkus.webauthn.login-page`**
  (Default `/login.html`), nicht mit 401. Im Spike mappt ein `@ServerExceptionMapper` für
  `UnauthorizedException` auf 401 JSON (`api.v1.PasskeyFehlerMapper`). Für Fälle, die vor
  JAX-RS greifen (Permission-Policies), gilt das nicht.
- Origin und rpId werden ohne Konfiguration aus der Request-URL abgeleitet. Hinter einem
  Reverse Proxy (E12) müssen `quarkus.webauthn.origins` und `quarkus.webauthn.relying-party.id`
  gesetzt werden.

**Folgen für den Client:**
- React-PWA unter **derselben Site** wie die API (z. B. `app.example.de` und `/v1` über denselben Host
  oder Proxy): Das Cookie-Modell passt. Der Browser sendet das Cookie automatisch mit, eine
  Token-Verwaltung entfällt. `SameSite=Strict` schützt weitgehend vor CSRF. Mutierende Requests
  sollten trotzdem einen CSRF-Schutz bekommen (Origin-Prüfung oder Header).
- API auf **anderer Site** als die PWA: Ein Cookie mit `SameSite=Strict` wird nicht mitgeschickt.
  Dann wäre `cookie-same-site=none` plus CORS mit Credentials nötig, was CSRF-relevant ist. Empfehlung: dieselbe Site.
- **Capacitor:** Die WebView-Origin ist `https://localhost` (Android) bzw. `capacitor://localhost` (iOS)
  und damit eine andere Site als die API. Cookies mit SameSite=Strict und WebAuthn in der WebView sind
  dort problematisch, native Passkeys brauchen ein Plugin. Die Origins sind dann
  `android:apk-key-hash:...` (Android) und erfordern Associated Domains bzw. Digital Asset Links.
  Das ist **im Spike nicht untersucht** und wahrscheinlich ein eigener Task. Eventuell braucht es
  dafür statt Cookie ein Bearer-Token. Die Ports lassen das zu, weil `Anmeldesitzung` austauschbar ist.

## 6. Befund: `BenutzerId`

- Technisch passt der Typ: Der User-Handle wird als UTF-8-Bytes von `BenutzerId.wert()` gebildet
  (höchstens 64 Byte, wird geprüft). Die Sitzung trägt `wert()` als Principal.
- Der Typ ist aber **zu offen**. WebAuthn verlangt, dass der User-Handle keine personenbezogenen
  Daten enthält (L3, §5.4.3). Ein beliebiger String (z. B. eine E-Mail) würde das verletzen,
  und mehr als 64 Byte sind unzulässig. **Empfehlung:** In Phase 2 auf UUID umstellen (E5):
  `record BenutzerId(UUID wert)`, User-Handle = 16 Rohbytes. Die Aufrufer aus S-02a sind nur
  an der Konstruktion betroffen. Im Spike ist das nicht umgestellt (Vorgabe).
- Der Spike vergibt `BenutzerId`s bereits als `UUID.randomUUID().toString()`.

## 7. Grenzen und was unverifiziert bleibt

- **Kein echter Browser, kein echtes Gerät.** `WebAuthnHardware` erzeugt nur Attestation `none`,
  ES256, rpId `localhost`, keine Transports und kein Backup-Flag. Damit ist unverifiziert:
  - das Zusammenspiel mit `navigator.credentials.create/get` und mit dem JSON-Format der Browser
    (`toJSON()` bzw. `PublicKeyCredential.parseCreationOptionsFromJSON`), insbesondere die
    Base64URL-Kodierung der Optionen, die webauthn4j serialisiert;
  - ob Plattform-Authenticatoren (iCloud-Schlüsselbund, Google Password Manager, Windows Hello)
    mit dem stabilen User-Handle tatsächlich **zwei getrennte** Passkeys anlegen bzw. per
    `excludeCredentials` die Doppelregistrierung verhindern;
  - discoverable Login ohne Benutzernamen (Account-Auswahl im Browser), RS256- oder EdDSA-Schlüssel,
    Signaturzähler 0 bei synchronisierten Passkeys;
  - Verhalten hinter dem Reverse Proxy (Origin/rpId) und mit HTTPS (`Secure`-Cookie);
  - Capacitor bzw. native Apps (siehe Abschnitt 5).
  Empfehlung: In Phase 2 oder C-03 einen E2E-Test mit dem Virtual Authenticator von Chrome DevTools
  bzw. Playwright (CDP `WebAuthn.addVirtualAuthenticator`) ergänzen.
- Ausstehende Registrierungen (Benutzername → vorgesehene `BenutzerId`) liegen im Spike unbegrenzt
  im Speicher, ohne Ablaufzeit. Das ist DoS-anfällig und muss in Phase 2 mit TTL in der DB liegen.
  Der Benutzername ist zwischen Optionen und Abschluss nicht reserviert, die Kollision wird beim Abschluss erkannt (409).
- `POST /v1/registrieren/optionen` antwortet bei vergebenem Namen mit 409. Damit lassen sich Konten
  aufzählen (vgl. quarkus#44292). Ob das akzeptiert wird, ist offen.
- OpenAPI: Optionen und Credentials sind als freier JSON-Text bzw. `object` beschrieben, ohne
  Schema. Nicht im Spike: Abmelden-Endpunkt, Passkey-Namen, "zuletzt benutzt", Zustand
  "nur ein Passkey", Einwilligung (S-08), Rollen (S-05), DB (S-12).
- Blockierende Aufrufe (`await().indefinitely()`) laufen in Worker-Threads der REST-Endpunkte. Das ist
  korrekt, aber nicht reaktiv.

## 8. Empfehlung

**Die Quarkus-WebAuthn-Extension verwenden, aber nur die programmatische API** (`WebAuthnSecurity`)
hinter den Ports aus dem Spike. Die Endpunkte unter `/q/webauthn` werden in Prod gesperrt.
Gründe:
- Die Kryptografie macht webauthn4j ohnehin, die Extension liefert zusätzlich Challenge-Cookie,
  Sitzungs-Cookie und Security-Integration (`@Authenticated`, `SecurityIdentity`). Das deckt den Bedarf.
- Der nötige Umweg ist klein und auf `QuarkusWebAuthnZeremonie` begrenzt.

Bedingungen bzw. Restrisiken, über die der Council entscheiden sollte:
1. `HttpOnly` fehlt beim Sitzungs-Cookie, das ist für Prod nötig. Lösung per Filter oder Upstream-Issue.
2. 302 statt 401 bei fehlender Anmeldung bzw. gesperrten Pfaden (Mapper und Proxy-Regel).
3. Capacitor bzw. native Apps: Das Cookie-Modell der Extension passt dort voraussichtlich nicht.
   Wird Capacitor Pflicht, ist `webauthn4j` direkt plus eigenes Token-/Sitzungsmodell die Alternative.
   Der Port-Schnitt erlaubt den Wechsel ohne Änderungen in `api`, `application` und `domain`.

**Aufwand Phase 2 (Server, ohne S-08 und S-12 selbst), Schätzung:**
- JPA-Adapter für Benutzer, Passkey und ausstehende Registrierung mit TTL, Flyway-Migration: 0,5–1 PT
- Härtung: HttpOnly-Filter, 401 überall, `/q/webauthn` in Prod sperren plus Test, Origins, rpId, Schlüssel per Env, CSRF-Prüfung: 1 PT
- API ausbauen: Abmelden, Passkey-Name und "zuletzt benutzt", Zustand "nur ein Passkey", OpenAPI-Schemas der Options/Credential-JSONs, `BenutzerId` auf UUID: 1 PT
- E2E mit Virtual Authenticator (Chrome/Playwright) gegen die PWA: 0,5–1 PT

Summe etwa **3–4 PT**. Capacitor bzw. native Passkeys sind nicht eingerechnet.

## 9. Offene Fragen an den Council

1. Wird das fehlende `HttpOnly` mit einem eigenen Filter umgangen, oder wird ein Upstream-Fix abgewartet?
2. Liegen API und PWA auf derselben Site (Voraussetzung für das Cookie-Modell)? Gehört Capacitor
   zum Umfang, und reicht dann Cookie, oder braucht es ein Token?
3. E12-Test "kein `/q/*` in Prod": Darf 302 (Deny-Policy mit WebAuthn-Challenge) als gesperrt gelten,
   oder wird am Proxy bzw. mit eigenem Mechanismus auf 404/403 gesperrt?
4. Wird die Aufzählung von Benutzernamen über `registrieren/optionen` (409) akzeptiert?
5. `BenutzerId` auf UUID umstellen (Empfehlung), im Zuge von S-04 Phase 2?
