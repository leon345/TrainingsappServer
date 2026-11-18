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
