# TrainingsappServer

Backend der Trainingsapp (Intervalltimer, Workout-/Calisthenics-Tracking, Passkey-Authentifizierung, Teilen/Profile und DSGVO-Funktionen). Das zugehörige Frontend liegt im Repository [TrainingsappClient](https://github.com/leon345/TrainingsappClient).

**Status:** Vollständige Umsetzung aller Muss-Kriterien (`GS-01`–`GS-05` auf Branch `gemini`).

---

## Technik & Architektur

- **Framework:** Quarkus `3.40.1.2` (LTS), Java 21, Maven
- **Architektur:** Domain-Driven Design in einer Layered Architecture (`domain`, `application`, `infrastructure`, `api.v1`), automatisiert geprüft durch ArchUnit (`ArchitekturTest`)
- **Persistenz:** PostgreSQL + Hibernate ORM Panache + Flyway (`V1`–`V4`) + Dev Services in `%dev`/`%test`
- **Authentifizierung & Sicherheit:** Passwortlose Passkeys (`quarkus-security-webauthn`, `ES256`/`RS256`, Multi-Passkey), RBAC (`USER`, `ADMIN`) & ABAC (`ZugriffsPolicy` + `@PermissionChecker`), Rate-Limiting (`quarkus-smallrye-fault-tolerance`), HTTP-Security-Header über `quarkus.http.header.*`
- **API & Betrieb:** Versionierte REST-API unter `/v1/*` mit einheitlichem Fehlerformat `FehlerV1`, OpenAPI-Export nach `target/openapi/`, Health-Checks auf dem separaten Management-Port (`9000`)

---

## Dokumentation

| Dokument | Inhalt |
| --- | --- |
| [docs/api-konventionen.md](docs/api-konventionen.md) | Schichtenarchitektur, `/v1`-Versionierung, OpenAPI, `FehlerV1`, Zeitdauern in ganzen Sekunden |
| [docs/auth-erweiterung.md](docs/auth-erweiterung.md) | Passkey-Architektur, dünner `Anmeldesitzung`-Port (Native-/Token-Erweiterbarkeit), RBAC/ABAC, Quarkus-`3.40.1.2`-Limitierungen |
| [docs/erweiterungen.md](docs/erweiterungen.md) | Interner `WorkoutImport`-Port für künftige externe Trainingsdatenquellen (z. B. Apple Health) inkl. Art.-9-DSGVO-Hinweisen |
| [docs/dsgvo-tom.md](docs/dsgvo-tom.md) | Verarbeitungsverzeichnis, TOM, Log-/Backup-Retention (14/30 Tage), Löschregeln & offene Betreiberpflichten |
| [docs/betrieb.md](docs/betrieb.md) | Strato-/EU-Betriebsvorgaben, Pflicht-Umgebungsvariablen, Reverse Proxy, Management-Port & Smoke-Check |
| [docs/prototyp-lokal.md](docs/prototyp-lokal.md) | Schritt-für-Schritt-Anleitung für den lokalen Vollbetrieb (Server + PostgreSQL Dev Services + Client) |

---

## Entwicklung, Build & Tests

Voraussetzungen: **Java 21**, **Maven 3.9+** und laufendes **Docker** (für PostgreSQL über Quarkus Dev Services).

```bash
# Vollständiger Build inkl. aller Tests (Domain, ArchUnit, Passkeys, Timer, Workouts, ABAC, DSGVO,
# Aufräumjob, %prod-Sicherheitsmatrix, IDOR-Komplettmatrix & OpenAPI-Export)
mvn clean verify

# Lokaler Entwicklungsserver (Port 8080, OpenAPI unter /q/openapi, Swagger UI unter /q/swagger-ui)
mvn quarkus:dev

# Live-/Staging-Smoke-Check (Security-Header, 401 auf /v1/*, 404 auf /q/*)
./scripts/smoke-check.sh https://training.example.de
```

- OpenAPI-Artefakte werden beim Build unter `target/openapi/openapi.yaml` und `target/openapi/openapi.json` erzeugt.
- Für den Produktionsbetrieb sind `DB_URL`, `DB_USER`, `DB_PASSWORD`, `WEBAUTHN_RP_ID`, `WEBAUTHN_ORIGINS` und `SESSION_ENCRYPTION_KEY` über Umgebungsvariablen zu setzen (Details siehe [docs/betrieb.md](docs/betrieb.md)).
