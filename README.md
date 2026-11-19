# TrainingsappServer

Backend der Trainingsapp (Intervalltimer und Workout-Tracking). Das zugehoerige Frontend liegt in [TrainingsappClient](https://github.com/leon345/TrainingsappClient).

Status: Planung. Die Anforderungen unten sind der Stand vom 17.11.2026 und werden als pruefbare Ziele (`goals/`) umgesetzt.

## Technik

- Quarkus 3.40 (LTS), kein anderes Backend-Framework
- Schnittstellen: REST, gRPC oder WebSockets (auch gemischt)
- API-Dokumentation mit OpenAPI (Quarkus)
- Aufbau nach Domain-Driven Design in einer Layered Architecture
- Nutzung der Quarkus-Packages (u. a. WebAuthn und Security-Annotationen)

## Muss-Kriterien

- Timer fuer Intervalltraining: Trainingsphase und Pause in Zeit einstellbar
- Workouts und Calisthenics-Workouts koennen getrackt werden
- Benutzerauthentifizierung ausschliesslich mit Passkeys (WebAuthn)
- Jeder Benutzer kann mehrere Passkeys anlegen
- RBAC und ABAC; Benutzer duerfen nur auf ihre eigenen Attribute zugreifen
- Alle Endpunkte nur authentifiziert erreichbar, ausser Anmelden und Registrieren
- Versionierte Endpunkte, z. B. `/v1/anmelden`
- Alle Endpunkte per OpenAPI dokumentiert
- Workout-Sichtbarkeit:
  - Privat: nur Besitzer und eingeladene Benutzer
  - Oeffentlich: im Profil des Besitzers und bei eingeladenen Benutzern sichtbar
- Konformitaet mit DSGVO und weiteren EU-Vorgaben
- Sicherheitstests und Funktionstests (Server- und Clientseite)

## Wunsch-Kriterien

- Weitere Benutzer zu einem Workout hinzufuegen (Lese-, optional Schreibrechte, vom Besitzer je Benutzer festlegbar)
- Schwimm-Workouts: Bahnen, Zeit pro Bahn, Schwimmtechnik
- Teilen der Trainingsdaten mit Apple Health

## Abgrenzung

- Anmeldung nur mit Passkeys, keine anderen Verfahren
- Backend nur mit Quarkus

## Offene Entscheidungen

- Wer sieht oeffentliche Workouts (alle angemeldeten Benutzer oder nur Eingeladene)?
- Welche Attribute gelten fuer ABAC?
- Unterstuetzt die Quarkus-WebAuthn-Extension mehrere Passkeys pro Benutzer? (Spike noetig)

## Entwicklung & Tests

Voraussetzungen: Java 21, Maven 3.9+ und laufendes Docker (fuer PostgreSQL ueber Quarkus Dev Services).

```bash
# Vollstaendiger Build inkl. aller Tests (ArchUnit, API, OpenAPI, PostgreSQL/Flyway, Prod-Profil)
mvn clean package

# Lokaler Entwicklungsserver (Port 8080, OpenAPI unter /q/openapi, Swagger UI unter /q/swagger-ui)
mvn quarkus:dev
```

- OpenAPI-Artefakte werden beim Build unter `target/openapi/` (`openapi.yaml` und `openapi.json`) erzeugt.
- Architektur- und API-Konventionen sind in [docs/api-konventionen.md](docs/api-konventionen.md) dokumentiert.
- Fuer den Produktionsbetrieb werden `DB_URL`, `DB_USER` und `DB_PASSWORD` ueber Umgebungsvariablen gesetzt; Health-Proben laufen ueber das Management-Interface (Port `9000`).
