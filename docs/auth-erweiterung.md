# Auth-Service: Erweiterungspunkt (S-04, E23)

Nutzerentscheid A (18.11.2026): Erstmal keine native App, aber der Auth-Service soll dafuer erweiterbar
sein. Der Council hat das mit E23 bestaetigt. Dieses Dokument beschreibt, wo erweitert wird, was dafuer
fehlt und was unveraendert bleibt.

Weitere Dokumente: `auth-sicherheit.md` (Befunde, Restrisiken, Update-Pruefung),
`auth-betrieb.md` (Umgebungsvariablen, Reverse Proxy, Startpruefung),
`auth-annahmen.md` (Annahmen und offene Punkte). `spike-s04-passkeys.md` ist der historische
Spike-Bericht; wo er vom heutigen Stand abweicht, gelten die hier genannten Dokumente.

## Schichten und Ports

| Port | Paket | Umsetzung heute (nur `infrastructure`) |
|---|---|---|
| `Anmeldesitzung` | `application.sitzung` | `infrastructure.sitzung.CookieAnmeldesitzung` (Cookie der Quarkus-WebAuthn-Extension) |
| `PasskeyZeremonie` | `application.passkey` | `infrastructure.passkey.QuarkusWebAuthnZeremonie` (`WebAuthnSecurity`, Challenge im Cookie) |
| `Transaktion` | `application` | `infrastructure.persistenz.QuarkusTransaktion` |
| `BenutzerRepository`, `AusstehendeRegistrierungRepository` | `domain.benutzer` | `infrastructure.benutzer.Panache...` |
| `PasskeyRepository` | `domain.passkey` | `infrastructure.passkey.PanachePasskeyRepository` |

`Anmeldesitzung` ist aus Sicht der Use-Cases geschnitten und liefert ein neutrales Ergebnis:

- `Optional<BenutzerId> aktuellerBenutzer()`
- `void anmelden(BenutzerId)`
- `void beenden()`
- dazu `angemeldeterBenutzer()` (Default-Methode, wirft `NichtAngemeldet`, abgebildet auf 401)

`application` kennt nur `BenutzerId`. `SecurityIdentity`, Vert.x-, Cookie- und Extension-Typen gibt
es nur in `infrastructure`; `@Authenticated` und `@RolesAllowed` bleiben in `api.v1` erlaubt (E23).
Das erzwingt `architektur.ArchitekturRegelnTest` (ArchUnit) fuer `domain`, `application` und `api.v1`
(keine Abhaengigkeit auf `io.vertx..`, `io.quarkus.security.webauthn..`, `SecurityIdentity`,
`jakarta.transaction..`).

Neben dem Port nutzt Quarkus Security einen `HttpAuthenticationMechanism`, damit `@Authenticated` und
die Pfad-Policy fuer `/v1/*` (E25) greifen. Heute ist das der Mechanismus der WebAuthn-Extension (Cookie);
`infrastructure.http.JsonAnmeldungErforderlich` liefert davor die Challenge als 401 mit JSON statt 302.

## Nachweis der Austauschbarkeit (Header-Sitzung, nur Testquellen)

In `src/test/java/de/trainingsapp/testhilfe/sitzung` liegt eine zweite Umsetzung von `Anmeldesitzung`:
ein serverseitig gespeichertes Token im Header `X-Sitzung` (`HeaderAnmeldesitzung`, `HeaderSitzungen`)
mit passendem Quarkus-Security-Mechanismus (`HeaderAuthMechanismus`). Sie ist als CDI-Alternative nur
im Testprofil `HeaderSitzungProfil` aktiv.

`api.v1.AnmeldesitzungAustauschTest` belegt: Registrierung, Anmeldung, Auflisten, Hinzufuegen,
Umbenennen, Loeschen und Abmelden laufen damit ohne Aenderung an `api.v1`, `application` oder `domain`;
Ablauf (TTL 15 Minuten, verstellbare Uhr) und Widerruf (Abmelden) sind getestet.

**Die Header-Variante kommt nie in die Hauptquellen.** Sie ist ein Testbeleg, keine Vorlage fuer Prod.
`ArchitekturRegelnTest.nurDieCookieSitzungInDenHauptquellen` laesst den Build scheitern, wenn in den
Hauptquellen eine andere Klasse als `CookieAnmeldesitzung` den Port umsetzt. Eine echte Token-Sitzung
fuer eine native App braeuchte einen eigenen Task (C-15) und einen eigenen Beschluss zu dieser Regel.

## Bekannte Grenze: die Challenge liegt im Cookie

Beim Cookie-Modell legt die Extension die WebAuthn-Challenge verschluesselt in das Cookie
`_quarkus_webauthn_challenge`; zwischen Optionen und Abschluss haelt der Server dafuer keinen Zustand.
Auch der Austauschtest nutzt fuer die Zeremonie noch dieses Cookie, nur die Sitzung danach laeuft
per Header.

Eine Token-Anmeldung ohne Cookies (native App) braucht deshalb einen **Server-Speicher fuer
ausgegebene Challenges** (Challenge, Zweck, Ablauf, einmaliger Verbrauch) und damit eine andere
Umsetzung von `PasskeyZeremonie`, z. B. direkt mit webauthn4j. Teile sind schon vorhanden:
`ausstehende_registrierung.challenge` bindet die Registrierung an ihre Challenge, und
`verbrauchte_challenge` macht jede Challenge einmal verwendbar. Fuer die Anmeldung und das Hinzufuegen
fehlt ein Speicher der *ausgegebenen* Challenges.

## Was fuer eine native App zu ergaenzen waere

1. **Token-Sitzung** als zweite Umsetzung von `Anmeldesitzung` plus `HttpAuthenticationMechanism`
   (Bearer-Token oder Header), mit serverseitigem Speicher, Ablauf und Widerruf. Neue Tabelle per Migration.
2. **Challenge-Speicher und Zeremonie ohne Cookie** (siehe oben).
3. **Native Passkeys und Zuordnung von App und Domain**: Die App-Origins (Android:
   `android:apk-key-hash:...`) muessen in die Allowlist. `WebAuthnKonfigurationPruefung` laesst heute
   nur `https://...` und `http://localhost` zu und muss dafuer erweitert werden. Dazu kommen die
   Zuordnungsdateien `/.well-known/assetlinks.json` (Android) und `/.well-known/apple-app-site-association`
   (iOS). Der Prod-Filter `QPfadSperre` sperrt davon nur `/.well-known/webauthn`; die Proxy-Regel (S-14)
   muss die Zuordnungsdateien dann zulassen. Fuer Related Origins (WebAuthn L3) ist der heute gesperrte
   Pfad `/.well-known/webauthn` gedacht; diese Sperre waere dann neu zu bewerten.
4. **Origin-Regel fuer Clients ohne Browser**: `infrastructure.http.OriginPruefung` weist jeden
   schreibenden Request unter `/v1` ohne `Origin`-Header mit 403 ab (Annahme dieser Umsetzung, siehe
   `auth-annahmen.md`). Native Apps, CLI-Werkzeuge oder Server-zu-Server-Aufrufe senden in der Regel kein
   `Origin`. Sie muessen den Header setzen, oder die Pruefung bekommt eine eigene Regel, etwa keine
   Pruefung bei Token-Sitzung (CSRF betrifft nur automatisch gesendete Cookies). Das ist eine
   Sicherheitsentscheidung fuer C-15.
5. Rate-Limit (E17) und Pfad-Policy (E25) gelten unveraendert; die Pfade bleiben gleich.

## Was dafuer NICHT geaendert werden muss

- `domain` (Benutzer, Benutzername, Passkey, Repositories) und das Datenbankschema der vorhandenen Tabellen.
- `application.passkey.PasskeyService` und alle Use-Cases (Registrieren, Anmelden, Hinzufuegen,
  Auflisten, Umbenennen, Loeschen mit Loeschsperre, Abmelden).
- `api.v1`: Ressourcen, DTOs, OpenAPI, `@Authenticated`; die Ressourcen kennen weder Cookie noch Token.
- Die Transaktionsgrenzen (E26) und die Regeln fuer Benutzernamen (E28) und Passkey-Bezeichnungen (E29).

Beleg: Der Austauschtest laeuft mit der Header-Sitzung, ohne eine Zeile in diesen Schichten zu aendern.
