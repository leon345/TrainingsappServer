# Auth-Service: Vorgaben fuer den Betrieb (S-04)

Zuarbeit fuer die Betriebsdoku (Task S-14). Hier stehen nur Namen und Bedeutungen, keine Werte.

## Umgebungsvariablen im Prod-Profil

Alle ohne Standardwert. Fehlt eine, scheitert der Start (Exit-Code ungleich 0).

| Variable | Bedeutung |
|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` | PostgreSQL (S-12, `docs/persistenz.md`) |
| `TRAININGSAPP_WEBAUTHN_ORIGINS` | Allowlist der Origins, kommagetrennt, je `https://host[:port]` ohne Pfad. Dient fuer WebAuthn und fuer die Origin-Pruefung schreibender Requests (E15). Bei einer Origin (E15) genau ein Eintrag: die Origin, unter der die PWA laeuft |
| `TRAININGSAPP_WEBAUTHN_RP_ID` | WebAuthn-rpId: Hostname der Origin oder eine uebergeordnete Domain. Nach dem ersten Passkey nicht mehr aendern, sonst sind alle Passkeys unbrauchbar |
| `TRAININGSAPP_COOKIE_SECURE` | Muss `true` sein (E14). Jeder andere Wert bricht den Start ab |
| `TRAININGSAPP_SITZUNG_SCHLUESSEL` | Schluessel fuer Sitzungs- und Challenge-Cookies, mindestens 32 Zeichen, geheim. Leer oder zu kurz bricht den Start ab. Ein Wechsel meldet alle Benutzer ab |
| `TRAININGSAPP_TRUSTED_PROXIES` | Adressen des Reverse Proxys (Format von `quarkus.http.proxy.trusted-proxies`, z. B. IP oder CIDR). Nur von dort werden `X-Forwarded-*` ausgewertet |

Optional, mit Standardwerten (als Umgebungsvariable ueberschreibbar, z. B. `TRAININGSAPP_RATE_LIMIT_ANFRAGEN`):

| Konfiguration | Standard | Bedeutung |
|---|---|---|
| `trainingsapp.rate-limit.anfragen` / `trainingsapp.rate-limit.fenster` | `10` / `PT10M` | Rate-Limit je Client-IP und Endpunkt (E17) |
| `trainingsapp.passkey.ausstehende-registrierung-ttl` | `PT10M` | Reservierung eines Benutzernamens zwischen Optionen und Abschluss |
| `trainingsapp.benutzername.gesperrt` | `admin,root,system,support,trainingsapp` | Gesperrte Benutzernamen (E28) |

## Voraussetzungen an den Reverse Proxy

1. **Eine Origin**: PWA unter `/`, API unter `/v1` auf demselben Host (E15). Kein CORS.
2. **Nur `/v1/*` und die PWA-Dateien weiterleiten**, alles andere 404 (E16, dritte Schicht).
   Die Anwendung sperrt `/q/*` und `/.well-known/webauthn` im Prod-Profil zusaetzlich selbst.
3. **TLS am Proxy**, zur Anwendung `X-Forwarded-Proto: https` setzen.
4. **`X-Forwarded-For` mit der echten Client-Adresse ueberschreiben, nicht anhaengen.** Quarkus zaehlt
   fuer das Rate-Limit den ersten Eintrag (getestet in `RateLimitTest.ersterEintragVonXForwardedForZaehlt`).
   Haengt der Proxy nur an (z. B. nginx `$proxy_add_x_forwarded_for`), bestimmt der Client den Wert selbst.
   Ebenso `X-Forwarded-Proto` und `X-Forwarded-Host` vom Client verwerfen und neu setzen.
5. **Anwendung nur ueber den Proxy erreichbar** (kein offener Port), und `TRAININGSAPP_TRUSTED_PROXIES`
   auf genau den Proxy setzen. Von anderen Adressen ignoriert Quarkus `X-Forwarded-*`
   (getestet in `RateLimitOhneVertrauenswuerdigenProxyTest`).
6. **Genau eine Instanz** der Anwendung (Rate-Limit im Speicher, siehe `auth-sicherheit.md`).
7. Der Header `Forwarded` (RFC 7239) wird nicht ausgewertet (`quarkus.http.proxy.allow-forwarded` ist nicht
   gesetzt, Standard aus); ausgewertet werden nur `X-Forwarded-*`. Das folgt aus der Quarkus-Konfiguration,
   ein eigener Test dafuer existiert nicht. Der Proxy sollte `Forwarded` vom Client trotzdem verwerfen.

## Startpruefung des gebauten JARs

`scripts/prod-start-pruefung.sh` (aus dem Repo-Wurzelverzeichnis, nach `mvn package`; braucht Docker
und curl) startet einen eigenen PostgreSQL-Container und das Fast-JAR im Prod-Profil fuenfmal:

| Fall | Erwartung |
|---|---|
| (a) ohne `TRAININGSAPP_*` | Exit 1, `Failed to load config value ... quarkus.webauthn.origins` |
| (b) leerer Sitzungsschluessel | Exit 1, `TRAININGSAPP_SITZUNG_SCHLUESSEL fehlt oder ist leer ...` |
| (c) Schluessel unter 32 Zeichen | Exit 1, `... muss mindestens 32 Zeichen lang sein` |
| (d) `TRAININGSAPP_COOKIE_SECURE=false` | Exit 1, `TRAININGSAPP_COOKIE_SECURE muss im Prod-Profil true sein (E14)` |
| (e) vollstaendig | Start; `/v1/info` 401 als JSON, `/q/openapi` 404 ohne Body, keine Zufallsschluessel-Warnung |

Alle Werte erzeugt das Skript selbst als Wegwerf-Werte; der Container wird am Ende per ID beendet.
Exit-Code des Skripts: 0, wenn alle Faelle wie erwartet sind. Ports per `PORT` (Standard 18080) und
`PG_PORT` (Standard 55432) aenderbar.

## Hinweise fuer eine kuenftige CI

- Datenbank-Tests (E19) und der Browser-E2E-Test sind Pflicht; sie brauchen Docker und Chromium.
  Ueberspringen nur lokal mit `-Ddatenbank.tests=aus` bzw. `-De2e.tests=aus` (WARN im Log), nie in CI.
- Der E2E-Test nutzt Playwright fuer Java 1.65.0 mit Chromium-Revision 1254 (Chrome for Testing
  158.0.8127.18). Fehlt der Browser in `~/.cache/ms-playwright`, laedt Playwright ihn beim ersten Lauf
  herunter (Netzzugang noetig, kein root).
