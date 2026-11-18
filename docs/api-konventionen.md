# API- und Architekturkonventionen (Server)

## Schichten (Layered Architecture nach DDD)

Basispaket: `de.trainingsapp`

| Paket | Inhalt | Darf abhaengen von |
|---|---|---|
| `domain.<kontext>` | Entities, Value Objects, Aggregate, Domain-Services, Ports (z. B. Repository-Interfaces) | nichts (kein JAX-RS, keine Persistenz) |
| `application.<kontext>` | Anwendungsfaelle/Services, orchestrieren die Domain | `domain` |
| `infrastructure.<kontext>` | Implementierungen der Ports (Persistenz, Konfiguration, externe Dienste) | `domain`, `application` |
| `api.v<N>` | REST-Ressourcen und DTOs einer API-Version | `application`, `domain` (nur zum Mappen in DTOs) |

`<kontext>` ist der fachliche Bereich (Bounded Context), z. B. `systeminfo`.
Referenzbeispiel `GET /v1/info`: `api.v1.SystemInfoResource` -> `application.systeminfo.SystemInfoService`
-> Port `domain.systeminfo.SystemInfoProvider`, umgesetzt in `infrastructure.systeminfo.ConfigSystemInfoProvider`.

## Neue Endpunkte

1. **Versionierter Pfad:** Jede Ressource beginnt mit einem Versionspraefix aus
   `de.trainingsapp.api.ApiVersion`, z. B. `@Path(ApiVersion.V1 + "/anmelden")`.
   Es gibt bewusst keinen globalen Root-Pfad, damit mehrere Versionen parallel laufen koennen.
2. **Paket:** Ressourcen und DTOs liegen in `api.v1` (bzw. `api.v2` usw.).
   Eine inkompatible Aenderung erzeugt eine neue Version, bestehende Versionen bleiben stabil.
3. **DTOs statt Domain-Objekten:** Die API serialisiert nie Domain-Objekte direkt,
   sondern eigene Records (z. B. `SystemInfoResponse`).
4. **OpenAPI-Pflicht:** Jeder Endpunkt hat
   - `@Tag` auf der Ressource,
   - `@Operation` mit eindeutiger `operationId` (Schema `<verb><Ressource>V<N>`, z. B. `getSystemInfoV1`) und `summary`,
   - `@APIResponse` fuer jeden moeglichen Statuscode,
   - `@Schema` auf DTOs; Schemanamen tragen das Versionssuffix (z. B. `SystemInfoV1`).
5. **Test:** Zu jedem Endpunkt gehoert ein `@QuarkusTest`, der den Pfad unter `/v<N>/...` aufruft.

## Fehlerformat

Fehler unter `/v1` haben einen JSON-Body nach Schema `FehlerV1`: `{"code": "...", "meldung": "..."}`.
`code` ist stabil und maschinenlesbar, `meldung` ein Text fuer Menschen (kann sich aendern).

| Status | `code` | Wann |
|---|---|---|
| 400 | `BENUTZERNAME_UNGUELTIG`, `BEZEICHNUNG_UNGUELTIG`, `PASSKEY_UNGUELTIG` | Eingabe oder Passkey-Antwort ungueltig |
| 401 | `NICHT_ANGEMELDET` | ohne Anmeldung, fuer alle `/v1/*` ausser `anmelden*`/`registrieren*`, auch unbekannte Pfade (E25) |
| 403 | `ORIGIN_UNGUELTIG` | schreibender Request (POST, PUT, PATCH, DELETE) ohne oder mit nicht erlaubtem `Origin` (E15) |
| 404 | `PASSKEY_NICHT_GEFUNDEN` | Objekt existiert nicht oder gehoert einem anderen Benutzer |
| 409 | `BENUTZERNAME_VERGEBEN`, `LETZTER_PASSKEY` | Konflikt mit dem Zustand |

Die 401 und 403 entstehen vor JAX-RS (Vert.x-Filter bzw. Security-Challenge in `infrastructure.http`)
und haben trotzdem dasselbe Format.

## Zeitdauern (projektweit)

- Dauern werden in der API **immer als ganze Sekunden** uebertragen, Typ `integer` (`int32`).
- Die Einheit steht im Feldnamen, z. B. `trainingSekunden`, `pauseSekunden`.
- Kein ISO-8601 (`PT1M30S`), keine Bruchteile, keine Minutenfelder. Die Umrechnung
  in Minuten fuer die Anzeige erledigt der Client.
- Minimum und Maximum stehen im OpenAPI-Schema (`@Schema(minimum, maximum)`) und werden
  per Bean Validation (`@Min`/`@Max`) geprueft. Die verbindlichen Grenzen definiert die Domain.

## Festgelegte, noch nicht umgesetzte Schnittstellen

### Timer-Einstellung (S-02b) – festgelegt, noch NICHT als Endpunkt umgesetzt

Grund: Laut README sind alle Endpunkte ausser Anmelden/Registrieren nur authentifiziert
erreichbar; die Endpunkte folgen daher erst nach S-04 (Passkey-Auth), S-05 (RBAC/ABAC)
und S-12 (PostgreSQL/Hibernate/Flyway). Domain, Application-Service und In-Memory-Adapter
gibt es bereits (S-02a).

- Endpunkte: `GET /v1/timer/einstellung`, `PUT /v1/timer/einstellung`
- Genau eine Einstellung je angemeldetem Benutzer, keine ID im Pfad.
- Schema `TimerEinstellungV1`:

| Feld | Typ | Pflicht | Minimum | Maximum | Bedeutung |
|---|---|---|---|---|---|
| `trainingSekunden` | integer (int32) | ja | 1 | 86400 | Dauer der Trainingsphase in Sekunden |
| `pauseSekunden` | integer (int32) | ja | 0 | 86400 | Dauer der Pause in Sekunden (0 = keine Pause) |

Grenzen entsprechen `domain.timer.IntervallEinstellung` (`MIN_TRAINING`, `MIN_PAUSE`, `MAX_DAUER`).
Zuordnung im Code: `api.v1` (S-02b) -> `application.timer.TimerEinstellungService`
(`lese`/`speichere`, Benutzer als `domain.benutzer.BenutzerId`) -> Port
`domain.timer.IntervallEinstellungRepository`, derzeit umgesetzt durch
`infrastructure.timer.InMemoryIntervallEinstellungRepository` (`@DefaultBean`, Daten nur im
Arbeitsspeicher; wird durch den Datenbank-Adapter aus S-12 ersetzt).

## OpenAPI abrufen

- Dokument: `/q/openapi` (YAML), `/q/openapi?format=json` (JSON)
- Swagger UI: `/q/swagger-ui` (nur Dev- und Test-Modus)
- Beim Build wird das Dokument zusaetzlich nach `target/openapi/` geschrieben.

## Bauen und Starten

Voraussetzung: JDK 21, Maven 3.9.

```bash
mvn quarkus:dev                               # Dev-Modus mit Live-Reload, http://localhost:8080
mvn package                                   # Build inkl. Tests
java -jar target/quarkus-app/quarkus-run.jar  # gebaute Anwendung starten
```

## Persistenz

Datenbank, Migrationen, Profile und Tests: siehe `docs/persistenz.md`.
