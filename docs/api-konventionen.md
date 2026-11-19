# API- und Architektur-Konventionen

Dieses Dokument beschreibt die verbindlichen Konventionen für das Trainingsapp-Backend (`de.trainingsapp`).

---

## 1. Schichtenarchitektur (DDD / Layered Architecture)

Alle fachlichen und technischen Bausteine werden strikt in vier Schichten unter `de.trainingsapp` getrennt und durch `ArchitekturTest` (ArchUnit) automatisch geprüft:

| Schicht | Paket | Verantwortung | Erlaubte / verbotene Abhängigkeiten |
| --- | --- | --- | --- |
| **Domain** | `de.trainingsapp.domain.<kontext>` | Fachobjekte, Wertobjekte, Domain-Exceptions (`DomainException`), Ports (Repository-/Provider-Interfaces) | **100 % framework-frei.** Keine Abhängigkeiten auf `application`, `infrastructure`, `api`, `io.quarkus..`, `io.vertx..`, `jakarta.*`, `org.eclipse.microprofile..` oder `org.jboss..`. |
| **Application** | `de.trainingsapp.application.<kontext>` | Use-Case-Services, Orchestrierung, Transaktionsgrenzen über Standard-`@Transactional` (`jakarta.transaction.Transactional`) | Hängt nur von `domain` ab. Kein `infrastructure`, kein `api`, kein `jakarta.persistence..`, kein `io.vertx..`, kein `io.quarkus.security.webauthn..`, kein `SecurityIdentity`. |
| **Infrastructure** | `de.trainingsapp.infrastructure.<kontext>` | Technische Adapter für Ports (Hibernate ORM Panache, Konfiguration, WebAuthn-Anbindung, externe Dienste) | Implementiert Ports aus `domain` bzw. `application`. Alle JPA-Entities, Panache-Repositories und `SecurityIdentity`-Zugriffe liegen ausschließlich hier. |
| **API** | `de.trainingsapp.api`, `de.trainingsapp.api.v1.<kontext>` | REST-Ressourcen, DTOs (`*Response` / `*Request`), OpenAPI-Annotationen, `@ServerExceptionMapper` | Hängt von `application` und `domain` ab, niemals direkt von `infrastructure`. Kein `jakarta.persistence..`, kein `io.vertx..`, kein `io.quarkus.security.webauthn..`, kein `SecurityIdentity`. |

---

## 2. Versionierung

- Alle öffentlichen REST-Endpunkte liegen unter dem gemeinsamen Pfadpräfix `ApiVersion.V1` (`/v1`), z. B. `GET /v1/info`.
- DTO-Schemanamen tragen das Versionssuffix `V1` (z. B. `SystemInfoV1`, `FehlerV1`).
- Rückwärtskompatible Erweiterungen (optionale Felder, neue Endpunkte) erfolgen in `/v1`. Inkompatible Änderungen erfordern eine neue Hauptversion (`/v2`).

---

## 3. OpenAPI-Dokumentation

- Jede Ressource und jede Operation wird vollständig mit MicroProfile OpenAPI dokumentiert:
  - `@Tag` an der Ressourcenklasse
  - `@Operation(operationId = "<name>V1", summary = "...", description = "...")` an jeder Methode
  - `@APIResponse` für alle erwarteten Statuscodes inkl. Fehlerantworten (`FehlerResponse` / `FehlerV1`)
  - `@Schema(name = "<Name>V1", ...)` an allen Request- und Response-DTOs
- Bei jedem Maven-Build (`mvn package`) wird die OpenAPI-Spezifikation automatisch nach `target/openapi/openapi.yaml` und `target/openapi/openapi.json` exportiert (`quarkus.smallrye-openapi.store-schema-directory=target/openapi`). Der Client generiert daraus seine TypeScript-Typen.
- Im `%dev`- und `%test`-Profil ist `/q/openapi` erreichbar; im `%prod`-Profil sind OpenAPI und Swagger UI deaktiviert (`quarkus.smallrye-openapi.enabled=false`, `quarkus.swagger-ui.enabled=false`).

---

## 4. Einheitliches Fehlerformat (`FehlerV1`)

Alle fachlichen Fehler, Validierungsfehler und unerwarteten Serverfehler der `/v1`-API liefern JSON im Format `FehlerV1` (`de.trainingsapp.api.v1.fehler.FehlerResponse`):

```json
{
  "code": "VALIDIERUNG_FEHLGESCHLAGEN",
  "meldung": "Die Anfrage enthält ungültige Eingaben."
}
```

Das zentrale Mapping erfolgt über Quarkus REST `@ServerExceptionMapper` (`ApiExceptionMapper`):

| Ausnahme | HTTP-Status | Fehlercode |
| --- | --- | --- |
| `ConstraintViolationException` | `400 Bad Request` | `VALIDIERUNG_FEHLGESCHLAGEN` |
| `DomainException` | `400 Bad Request` | `exception.code()` |
| `IllegalArgumentException` | `400 Bad Request` | `VALIDIERUNG_FEHLGESCHLAGEN` |
| Unerwartete `Exception` | `500 Internal Server Error` | `INTERNER_FEHLER` (ohne interne Details / Stacktrace) |

---

## 5. Zeitdauern

- Alle Zeitdauern werden projektweit als ganze Sekunden (`integer`) übertragen — **kein ISO-8601** (z. B. kein `PT30S`).
- Die Einheit steht immer im Feldnamen: `trainingSekunden`, `pauseSekunden`, `dauerSekunden`.
- Unter- und Obergrenzen werden konsistent per Bean Validation (`@Min`, `@Max`) und `@Schema(minimum = ..., maximum = ...)` dokumentiert.

---

## 6. Sicherheit, Betriebs-Endpunkte & HTTP-Header

- **Sicherheits-Annotationen in `api.v1`:** Für Authentifizierung und Autorisierung werden die idiomatischen Quarkus-Security-Annotationen `@Authenticated`, `@RolesAllowed`, `@PermissionsAllowed` genutzt. Der Zugriff auf `SecurityIdentity` und WebAuthn-Details bleibt auf `infrastructure` beschränkt.
- **HTTP-Sicherheits-Header:** Werden zentral über Quarkus-Bordmittel (`quarkus.http.header.*`) in `application.properties` gesetzt:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: no-referrer`
  - `Content-Security-Policy: default-src 'self'`
- **Health & Management-Interface:** Health-Checks laufen über das separate Quarkus-Management-Interface (`quarkus.management.enabled=true`, Prod-Port `9000`) und sind auf dem öffentlichen API-Port nicht erreichbar (`404`).
- **Datenbank-Zugangsdaten:** Im Prod-Profil ausschließlich über Umgebungsvariablen (`DB_URL`, `DB_USER`, `DB_PASSWORD`) ohne Defaults. Entwicklung und Tests nutzen PostgreSQL über Quarkus Dev Services und Flyway-Migrationen (`src/main/resources/db/migration/`).
