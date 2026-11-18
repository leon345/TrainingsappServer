# Auth-Service: sicherheitsrelevantes Verhalten, Befunde und Restrisiken (S-04)

Stand: Branch `task/S-04-passkeys`, Quarkus 3.40.1.2, `quarkus-security-webauthn` 3.40.1.2
(intern webauthn4j 0.30.3). Beschluesse: E13 bis E17, E23, E25, E26, E28, E29.

## Ablauf und Pruefungen

| Schritt | Endpunkt | Geprueft |
|---|---|---|
| Registrierung starten | `POST /v1/registrieren/optionen` | Rate-Limit, Origin, Benutzername (E28, gesperrte Namen), nicht vergeben; Reservierung der `BenutzerId` (TTL), Bindung an die ausgegebene Challenge |
| Registrierung abschliessen | `POST /v1/registrieren` | Rate-Limit, Origin; WebAuthn-Pruefung (Challenge, Origin, rpId, UV, Algorithmus); Challenge einmalig; Reservierung nur mit derselben Challenge; Benutzer und Passkey in einer Transaktion (E26); Sitzung erst nach dem Commit |
| Anmeldung starten | `POST /v1/anmelden/optionen` | Rate-Limit, Origin; neue Challenge, keine `allowCredentials` |
| Anmeldung abschliessen | `POST /v1/anmelden` | Origin; WebAuthn-Pruefung inkl. Signaturzaehler; Challenge einmalig; User-Handle muss zum Besitzer des Passkeys passen |
| Verwaltung | `/v1/passkeys...`, `/v1/abmelden` | Pfad-Policy und `@Authenticated` (401 als JSON), Origin bei schreibenden Requests; der Benutzer kommt nur aus der Sitzung |

Tests dazu: `api.v1.PasskeyZeremonieSicherheitTest`, `PasskeyAkzeptanzTest`, `PfadPolicyTest`,
`infrastructure.http.*Test`, `e2e.PasskeyBrowserE2ETest`.

## In S-04 geschlossene Luecken (B1)

1. **Benutzername im Abschluss nicht an den Start gebunden.** Ein Abschluss mit fremdem Namen haette eine
   fremde Reservierung uebernehmen koennen. Jetzt traegt `ausstehende_registrierung.challenge` die
   ausgegebene Challenge; nur eine Antwort auf genau diese Challenge schliesst ab, sonst bleibt die
   Reservierung unberuehrt.
2. **User-Handle bei der Anmeldung nicht geprueft.** Die Extension prueft ihn nicht. Jetzt muss er
   vorhanden sein und zum Besitzer des Passkeys passen (WebAuthn L3, 7.2).
3. **Challenge mehrfach verwendbar.** Die Challenge liegt nur im Cookie; eine abgefangene Antwort samt
   Cookie liess sich bis zum Ablauf des Cookies erneut einreichen. Bei Signaturzaehler 0 faengt das auch
   die Zaehlerpruefung nicht. Jetzt wird jede Challenge nach erfolgreicher Pruefung in
   `verbrauchte_challenge` (SHA-256) vermerkt; ein zweiter Verbrauch scheitert. Ausserdem erzeugt jeder
   Optionen-Abruf eine neue Challenge (die Extension haette die aus dem mitgeschickten Cookie wiederverwendet).

Ohne Befund geprueft (Eigenschaften von Extension bzw. webauthn4j, mit Tests): Origin und rpId gegen
die Allowlist, zurueckgehender Signaturzaehler, Wiederverwendung einer Registrierungsantwort,
Hinzufuegen nur fuer den angemeldeten Benutzer.

## Befunde zur Extension und ihre Behandlung

| Befund | Behandlung | Beleg |
|---|---|---|
| Eingebaute Endpunkte koennen einem angemeldeten Benutzer keinen weiteren Passkey anlegen (quarkus#44292: neuer Zufalls-User-Handle je Aufruf, `excludeCredentials` leer) | Nur die programmatische API `WebAuthnSecurity` mit eigenen `/v1`-Endpunkten; User-Handle (16 Rohbytes der `BenutzerId`) und `excludeCredentials` setzt `QuarkusWebAuthnZeremonie` selbst; `register`/`login` unter `/q/webauthn` bleiben aus (E13) | `PasskeyAkzeptanzTest`, `PasskeyBrowserE2ETest`, `WebAuthnExtensionBefundTest` |
| Sitzungs- und Challenge-Cookie ohne `HttpOnly` (fest im Code der Extension) | Vert.x-Filter `infrastructure.http.CookieHaertung` setzt `HttpOnly`, `SameSite=Strict` und in Prod `Secure` auf genau die Cookies, die in der Antwort gesetzt oder geloescht werden (E14); nutzt die interne Vert.x-API `ServerCookie` | `CookieHaertungTest`, `ProdProfilSitzungTest`, E2E (`document.cookie`) |
| `/q/webauthn/*` und `/.well-known/webauthn` nicht konfigurierbar, immer aktiv; Deny-Policy antwortet mit 302 | Prod: Route-Filter `QPfadSperre` mit 404 ohne Body und ohne `Set-Cookie` vor Security und Handlern; Deny-Policy als zweite Schicht; Proxy-Regel als dritte (E16) | `QPfadSperreProdTest` (inkl. kein SQL, keine Reservierung), `scripts/prod-start-pruefung.sh` |
| Ohne Anmeldung 302 auf `/login.html` | Mechanismus `JsonAnmeldungErforderlich` (hoehere Prioritaet) liefert 401 im Format `FehlerV1`; Pfad-Policy `authenticated` fuer `/v1/*` ausser `anmelden*`/`registrieren*` (E25) | `PfadPolicyTest`, `SystemInfoResourceTest` |
| EdDSA: `fromRequiredPersistedData` erzeugt den Algorithmus als neue Instanz, webauthn4j prueft auf Identitaet; EdDSA-Passkeys waeren registrierbar, aber nie nutzbar | `PasskeyWebAuthnUserProvider` baut den Datensatz fuer EdDSA selbst; EdDSA in `quarkus.webauthn.public-key-credential-parameters` aktiviert (Standard waere nur ES256, RS256) | `PasskeyAlgorithmenTest`, `PasskeyRecordAlgorithmenTest` (inkl. Befund-Test) |
| Fehlt `quarkus.http.auth.session.encryption-key`, erzeugt die Extension still einen Zufallsschluessel (nur WARN mit dem Schluessel im Log) | Prod: Variable ohne Standardwert; leer, zu kurz (unter 32 Zeichen) oder `Secure` ungleich `true` bricht `ProdKonfigurationPruefung` den Start ab | `ProdKonfigurationPruefungTest`, `ProdUmgebungsvariablenTest`, `scripts/prod-start-pruefung.sh` |
| Origin und rpId ohne Konfiguration aus dem Host-Header abgeleitet | Pflicht-Allowlist `quarkus.webauthn.origins` und `quarkus.webauthn.relying-party.id`, beim Start von `WebAuthnKonfigurationPruefung` geprueft | `WebAuthnKonfigurationPruefungTest`, `OriginAllowlistTest` |

### Befund-Tests mit eigenem Profil

Die Befund-Tests halten das Verhalten der Extension selbst fest (E13). Zwei davon laufen in einem
Profil, das genau eine eigene Gegenmassnahme per `quarkus.arc.exclude-types` ausschliesst, damit sie
weiter dieselbe Aussage ueber die Extension treffen:

- `WebAuthnSitzungsCookieBefundTest` ohne `CookieHaertung`: Die Extension setzt kein `HttpOnly`.
- `WebAuthnQPfadeGesperrtTest` ohne `JsonAnmeldungErforderlich`: Die Deny-Policy antwortet mit 302.

Mit den Gegenmassnahmen gilt das Gegenteil; das belegen die Haertungstests.

## Bei jedem Quarkus-Update pruefen

1. Alle Befund-Tests gruen (`WebAuthnExtensionBefundTest`, `WebAuthnSitzungsCookieBefundTest`,
   `WebAuthnQPfadeGesperrtTest`, `PasskeyRecordAlgorithmenTest.befundExtensionKannEdDsaNichtLaden`).
   Schlaegt einer fehl, hat sich das Verhalten geaendert; Befund und Umgehung neu bewerten.
2. Stand von quarkus#44292. Wird es behoben, kann der Umbau der Registrierungsoptionen entfallen.
3. `CookieHaertung` nutzt `io.vertx.core.http.impl.ServerCookie` und die Reihenfolge, in der Vert.x
   `Set-Cookie` nach den Headers-End-Handlern schreibt; `CookieHaertungTest` faellt bei Aenderungen auf.
4. `PasskeyWebAuthnUserProvider.findByUsername` liefert bewusst immer eine leere Liste. Das setzt voraus,
   dass die Extension das Ergebnis nur fuer die abgeschalteten Stellen nutzt.
5. Playwright-Version und Chromium-Revision fuer den E2E-Test (siehe `auth-betrieb.md`).

## Restrisiken

- **Sitzung zustandslos, kein Widerruf vor Ablauf.** Das Cookie `quarkus-credential` traegt die
  `BenutzerId` verschluesselt; der Server haelt keine Sitzungen. Abmelden loescht nur das Cookie im
  Browser. Ein kopiertes Cookie bleibt bis zum Leerlauf-Ablauf gueltig (Standard der Extension:
  `quarkus.webauthn.session-timeout` 30 Minuten, Erneuerung nach 1 Minute, nicht eigens konfiguriert).
  Das folgt aus der Bauart der Extension; ein Test fuer ein kopiertes Cookie nach dem Abmelden existiert
  nicht. Gemildert durch `HttpOnly`, `Secure`, `SameSite=Strict`, CSP und Origin-Pruefung.
  "Alle Sitzungen abmelden" ist nicht moeglich.
- **Gueltigkeit der Challenge.** Das Challenge-Cookie nutzt dieselbe Leerlaufzeit (30 Minuten), nicht das
  `timeout` der Optionen (5 Minuten). Der einmalige Verbrauch verhindert die Wiederverwendung, nicht eine
  spaete erste Verwendung.
- **Aufzaehlbarkeit der Benutzernamen** ueber 409 bei `registrieren/optionen` (E17, hingenommen). Gemildert
  durch das Rate-Limit; Restrisiko gehoert in die TOM.
- **Rate-Limit im Speicher der Instanz** (10 Anfragen je 10 Minuten und Endpunkt je Client-IP). Gilt nur
  bei **genau einer Instanz**; bei mehreren Instanzen vervielfacht sich das Kontingent. Nach einem
  Neustart beginnen die Zaehler neu. Nutzer hinter derselben NAT-Adresse teilen sich das Kontingent.
- **Client-IP hinter dem Proxy** nur korrekt, wenn der Proxy `X-Forwarded-For` ueberschreibt und die
  Anwendung nur ueber ihn erreichbar ist (siehe `auth-betrieb.md`). Quarkus wertet den ersten Eintrag aus.
- **Kein serverseitiger Speicher ausgegebener Anmelde-Challenges** (siehe `auth-erweiterung.md`).
- **Ausstehende Registrierungen und verbrauchte Challenges** werden nur beim naechsten `reserviere` bzw.
  `verbrauche` aufgeraeumt; ein taeglicher Aufraeumjob folgt mit S-16.
- **Attestation "none"**: Herkunft und Modell des Authenticators werden nicht geprueft (bewusst; E29: keine
  Geraetedaten, kein AAGUID gespeichert).
- **Konto ohne Passkey ist endgueltig verloren** (Wiederherstellung nur ueber Passkeys; offene Nutzerfrage 3).
