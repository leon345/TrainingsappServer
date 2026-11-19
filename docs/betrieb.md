# Betriebsanleitung (Strato / EU-Produktivbetrieb)

Stand: 19.11.2026 | Geltungsbereich: `trainingsapp-server` (Quarkus `3.40.1.2`, Java 21)

---

## 1. Zielarchitektur & Betriebsmodell

Gemäß den Betriebsentscheiden (Nutzerentscheide E, F und G) liegen Hosting, externe PostgreSQL-Datenbank, TLS-Zertifikate, Container-Deployment und Kubernetes-/k3s-Manifeste beim Betreiber (Strato, Rechenzentrum in Deutschland / EU):

```text
Internet (HTTPS / TLS 1.2+)
        │
        ▼
Reverse Proxy / Ingress (TLS-Terminierung, HSTS, X-Forwarded-Proto: https)
  ├── /          ──► Client (PWA / Nginx, statische Assets, SPA-Fallback)
  ├── /v1/*      ──► Server (trainingsapp-server, HTTP-Port 8080)
  └── /q/*       ──► GESPERRT (404 / 403 am Proxy als Defense-in-Depth)

Internes Cluster-/Host-Netz (nicht öffentlich!)
  └── :9000/q/health/live & /q/health/ready ──► Liveness- & Readiness-Probes
```

### Verbindliche Betriebsvorgaben
1. **Gleiche Origin für PWA & API:** Client (`/`) und Server (`/v1/*`) müssen unter **derselben Origin** (z. B. `https://training.example.de`) ausgeliefert werden. Dadurch ist im Produktivbetrieb kein CORS erforderlich und das Session-Cookie (`SameSite=Strict`) funktioniert nahtlos.
2. **TLS am Reverse Proxy & Proxy-Header:** Der Reverse Proxy terminiert TLS und setzt zwingend `X-Forwarded-Proto: https` (sowie `X-Forwarded-Host`). Der Server hat `quarkus.http.proxy.proxy-address-forwarding=true` aktiv und setzt dadurch automatisch das `Secure`-Flag auf dem Session- und Challenge-Cookie. Empfohlen am Reverse Proxy: `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
3. **Externe PostgreSQL-Datenbank:** PostgreSQL (empfohlen Version 16–18) läuft extern beim Betreiber (z. B. Strato / eigener DB-Server in DE). Flyway führt beim Start automatisch die Migrationen (`V1`–`V4`) aus (`quarkus.flyway.migrate-at-start=true`), Hibernate validiert das Schema (`validate`).
4. **Reverse-Proxy-Sperre für `/q/*`:** Im `%prod`-Profil deaktiviert Quarkus OpenAPI & Swagger UI und der `WebAuthnStandardRoutenBlocker` sperrt `/q/webauthn/*` mit `404`. Zusätzlich sollte der Reverse Proxy alle Anfragen an `/q/*` auf dem öffentlichen Port direkt blockieren (Defense-in-Depth).
5. **Offene Registrierung:** Die Registrierung ist bewusst offen ohne Einladungs-/Registrierungscode (Nutzerentscheid G). Missbrauchsschutz erfolgt über `@RateLimit` auf den Auth-, Such- und Profil-Endpunkten (ggf. ergänzt durch IP-Rate-Limits am Reverse Proxy).

---

## 2. Pflicht-Umgebungsvariablen (`%prod`-Profil)

Im `%prod`-Profil existieren **keine** hartcodierten Zugangsdaten oder Secrets. Alle folgenden Variablen müssen gesetzt sein, bevor der Server startet:

| Umgebungsvariable | Quarkus-Property | Beschreibung & Beispiel |
| --- | --- | --- |
| `DB_URL` | `%prod.quarkus.datasource.jdbc.url` | JDBC-URL zur externen PostgreSQL-Datenbank, z. B. `jdbc:postgresql://db.intern:5432/trainingsapp?sslmode=require` |
| `DB_USER` | `%prod.quarkus.datasource.username` | Datenbank-Benutzername mit Rechten für Flyway-Migrationen & DML |
| `DB_PASSWORD` | `%prod.quarkus.datasource.password` | Datenbank-Passwort (aus Secret-Store / Kubernetes Secret) |
| `WEBAUTHN_RP_ID` | `%prod.quarkus.webauthn.relying-party.id` | Relying-Party-ID (effektive Domain ohne Schema/Port), z. B. `training.example.de` |
| `WEBAUTHN_ORIGINS` | `%prod.quarkus.webauthn.origins` | Kommagetrennte Origin-Allowlist inkl. Schema, z. B. `https://training.example.de` |
| `SESSION_ENCRYPTION_KEY` | `%prod.quarkus.http.auth.session.encryption-key` | Kryptografisch zufälliger Schlüssel (**mind. 32 Zeichen**) für die AES-GCM-Verschlüsselung des WebAuthn-Session-Cookies (z. B. `openssl rand -base64 32`) |

### Optionale Konfigurationsvariablen

| Property / Env-Variable | Standardwert | Beschreibung |
| --- | --- | --- |
| `QUARKUS_HTTP_PORT` | `8080` | Öffentlicher API-Port (hinter Reverse Proxy) |
| `QUARKUS_MANAGEMENT_PORT` | `9000` | Interner Management-Port für Health-Checks (nicht öffentlich freigeben!) |
| `TRAININGSAPP_BENUTZERNAME_SPERRLISTE` | `admin,root,system,support,trainingsapp` | Zusätzliche gesperrte Benutzernamen (ergänzt die Domain-Pflichtliste) |
| `TRAININGSAPP_AUFRAEUMEN_CRON` | `0 15 3 * * ?` | Cron-Ausdruck (UTC) für den täglichen Aufräumjob (abgelaufene Registrierungen & passkeylose Konten > 14 Tage) |

---

## 3. Health-Checks & Monitoring

Health-Endpunkte (`quarkus-smallrye-health`) laufen ausschließlich auf dem separaten Management-Interface (`quarkus.management.enabled=true`, Port `9000`):

- **Liveness:** `GET http://<pod-oder-host>:9000/q/health/live`
- **Readiness:** `GET http://<pod-oder-host>:9000/q/health/ready` (prüft u. a. die Datenbankverbindung)

Auf dem öffentlichen API-Port (`8080`) liefern `/q/health`, `/q/health/live` und `/q/health/ready` stets `404`.

---

## 4. Bekannte Plattform-Limitierungen (`Quarkus 3.40.1.2`)

1. **Kein `HttpOnly` bei `quarkus-security-webauthn:3.40.1.2`:** Das Session-Cookie (`quarkus-credential`) und Challenge-Cookie (`_quarkus_webauthn_challenge`) tragen in Quarkus `3.40.1.2` kein `HttpOnly`-Flag. Kompensiert durch `SameSite=Strict`, `Secure` (über `X-Forwarded-Proto: https`) und strikte CSP (`default-src 'self'`).
2. **Kein `EdDSA` (`webauthn4j-core:0.30.3.RELEASE`):** Aufgrund eines Upstream-Bugs sind per Konfiguration `ES256,RS256` aktiviert. Details siehe [auth-erweiterung.md](auth-erweiterung.md).

---

## 5. Build, Start & Smoke-Check

```bash
# Produktions-Artefakt bauen (inkl. aller Tests & OpenAPI-Export)
mvn clean verify

# Start im Prod-Profil (mit gesetzten Pflicht-Umgebungsvariablen)
java -jar target/quarkus-app/quarkus-run.jar

# Automatisierter Live-/Staging-Smoke-Check gegen die öffentliche URL
./scripts/smoke-check.sh https://training.example.de
```
