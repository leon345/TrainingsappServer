# Auth-Service: Annahmen und offene Punkte (S-04, Phase A und B)

Annahmen sind Festlegungen, die weder Task-Datei noch Beschluesse vorgeben; sie sind im Code so umgesetzt.
"Abgenommen" heisst: im Auftrag an den Umsetzer ausdruecklich bestaetigt.

## Annahmen

| Thema | Umsetzung | Stand |
|---|---|---|
| Tabelle `passkey` | Technischer Schluessel `id uuid` (Konvention), fachlich `credential_id text UNIQUE`; die API nutzt die Credential-ID | Phase A |
| Loeschen eines Benutzers | `ON DELETE CASCADE` auf seine Passkeys (Kontoloeschung E7) | Phase A |
| Ausstehende Registrierung | TTL 10 Minuten (`trainingsapp.passkey.ausstehende-registrierung-ttl`); erneuter Start innerhalb der TTL behaelt die `BenutzerId` (User-Handle), die zuletzt ausgegebene Challenge gilt; Aufraeumen beim naechsten Reservieren | Phase A, Bindung an die Challenge Phase B |
| Einmalige Challenge | Tabelle `verbrauchte_challenge` (SHA-256), Aufbewahrung bis Ablauf des Challenge-Cookies plus 1 Minute, Aufraeumen beim naechsten Verbrauch | Phase B (B1) |
| Rate-Limit | 10 Anfragen je 10 Minuten je Client-IP und Endpunkt, festes Fenster, im Speicher | abgenommen |
| Fehlender `Origin`-Header | Schreibende Requests unter `/v1` ohne `Origin` werden mit 403 abgewiesen; geprueft nur unter `/v1` | abgenommen |
| `Secure` in Prod | `TRAININGSAPP_COOKIE_SECURE` ohne Standardwert, nur `true` zulaessig | abgenommen |
| Sitzungsschluessel | mindestens 32 Zeichen (die Extension verlangt selbst mehr als 16) | Phase B |
| "Passkey n" | Zaehler `benutzer.letzte_passkey_nummer`, steigt bei jedem neuen Passkey um 1; geloeschte und umbenannte Nummern zaehlen weiter (ein in "Passkey 9" umbenannter Passkey aendert den Zaehler nicht); ein Rollback gibt die Nummer frei | Phase B (E29, offener Punkt geklaert) |
| Passkey-Bezeichnung | NFC, dann `String.strip()`, 1 bis 50 Code Points; verboten sind Unicode-Kategorien Cc, Cf und einzelne Surrogate | Phase B (E29) |
| Keine Geraetedaten | AAGUID wird nicht gespeichert (E29 "keine Geraete- oder Attestierungsdaten"); der Extension wird ein Null-AAGUID uebergeben | Phase B |
| Sortierung der Passkeys | `erstellt_am`, am selben Tag nach Credential-ID | Phase B |
| Fremder Passkey | 404 wie ein nicht vorhandener (keine Unterscheidung) | Spike/Phase A |
| Algorithmen | `EdDSA,ES256,RS256` (Reihenfolge der Empfehlung WebAuthn L3) | Phase B |
| CSP | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'`, dazu `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` | Phase B |
| Header-Sitzung (nur Tests) | Token im Header `X-Sitzung`, TTL 15 Minuten | Phase B |
| E2E-Test | Pflicht wie Datenbank-Tests (E19), Ueberspringen nur mit `-De2e.tests=aus` | abgenommen |

## Befund zu E28 (gemeldet, nicht geaendert)

Regex und Beschreibung stimmen ueberein. Durch die Umwandlung mit `toLowerCase(Locale.ROOT)` *vor* der
Regex werden aber einzelne Nicht-ASCII-Eingaben zu ASCII und angenommen, z. B. das Kelvin-Zeichen U+212A
zu `k` (`"\u212Aai"` wird `kai`). Gespeichert wird trotzdem nur ASCII. Festgehalten in
`BenutzernameTest.kelvinZeichenWirdZuAsciiK`.

## Offene Punkte

| Punkt | Wohin |
|---|---|
| Health und Management-Port (E12) fehlen im Repo ganz; `/q/health` liefert auf dem Hauptport 404. Dass der Prod-Filter den Management-Port nicht trifft, ist ungeprueft | eigener Task |
| `/q/openapi` ist in Prod nur durch den Filter gesperrt; die OpenAPI-Extension selbst ist dort nicht abgeschaltet (E12) | Health-/Betriebs-Task |
| Registrierung per Code mit Obergrenze: Pruefung gehoert in die Transaktion von `PasskeyService.registrierungAbschliessen` vor das Anlegen des Benutzers; ein neuer Endpunkt muss in `RateLimit.PFADE` und, falls ohne Anmeldung erreichbar, in die Pfad-Policy `v1-oeffentlich` | S-15 |
| Taeglicher Aufraeumjob fuer `ausstehende_registrierung` und `verbrauchte_challenge`; Konten ohne Passkey entstehen durch die atomare Registrierung (E26) nicht | S-16 |
| Proxy-Regeln, TLS, Umgebungsvariablen (`auth-betrieb.md`) | S-14 |
| Rollen: `CookieAnmeldesitzung` liefert eine Identitaet ohne Rollen; `@RolesAllowed` braucht Rollen ueber `WebAuthnUserProvider.getRoles` oder einen `SecurityIdentityAugmentor`. Objektpruefungen nach E4 gehoeren in `application` | S-05 |
| Timer-Endpunkte: `Anmeldesitzung.angemeldeterBenutzer()` und Port `Transaktion` nutzen; Pfad-Policy und Origin-Pruefung gelten fuer `/v1/timer/*` schon | S-02b |
| Einwilligung bei der Registrierung (E7) | S-08 |
| Native App, Token-Sitzung, Challenge-Speicher, Origin-Regel fuer Clients ohne Browser (`auth-erweiterung.md`) | C-15 |
| UI-Hinweis "keine E-Mail, kein Klarname" und aktive Aufforderung zum zweiten Passkey | Client (C-03) |
| Upstream-Issue zu `HttpOnly` (E14) ist nicht angelegt; der Umsetzer hat keinen Zugang nach aussen genutzt | Orchestrator |
| Ein kopiertes Sitzungs-Cookie bleibt nach dem Abmelden bis zum Ablauf gueltig; nicht durch Test belegt | Restrisiko |
| Konto ohne Passkey endgueltig verloren | offene Nutzerfrage 3 |
| Echte Plattform-Authenticatoren (iCloud, Google, Windows Hello), andere Browser, HTTPS hinter dem Proxy | C-07, C-03, S-14 |
