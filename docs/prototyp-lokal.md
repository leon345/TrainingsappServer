# Lokaler Vollbetrieb (Prototyp: Server + PostgreSQL Dev Services + Client)

Diese Anleitung beschreibt den lokalen Start von Backend und Frontend für Entwicklung und Fullstack-Tests.

---

## Voraussetzungen

- **Java 21** und **Maven 3.9+**
- **Docker** (laufender Daemon für PostgreSQL über Quarkus Dev Services)
- **Node.js 22 oder 24** (für das Client-Repository)

---

## 1. Server starten (`server/`)

```bash
mvn quarkus:dev
```

- Startet automatisch einen lokalen PostgreSQL-Container (Dev Services) und führt alle Flyway-Migrationen (`V1`–`V4`) aus.
- API erreichbar unter: `http://localhost:8080/v1/...`
- OpenAPI-Schema: `http://localhost:8080/q/openapi`
- Swagger UI: `http://localhost:8080/q/swagger-ui`
- Dev-CORS und WebAuthn-Origins sind bereits für `http://localhost:5173` und `http://localhost:8080` vorkonfiguriert.

---

## 2. Client starten (`client/`)

In einem zweiten Terminal im Client-Verzeichnis:

```bash
npm ci
npm run dev
```

- Frontend erreichbar unter: `http://localhost:5173`
- Der Vite-Dev-Server leitet `/v1/*`-Anfragen per Proxy an `http://localhost:8080` weiter (gleiche Origin `localhost:5173` für Passkeys und Cookies).

---

## 3. Tests ausführen

### Server (inkl. PostgreSQL Dev Services, ArchUnit, Security- & IDOR-Matrix)
```bash
# Im server/-Verzeichnis (nicht parallel zu einem laufenden quarkus:dev auf Port 8081)
mvn clean verify
```

### Fullstack-Integrationstests (Client + laufender Server)
Bei laufendem `mvn quarkus:dev` im Client-Verzeichnis:

```bash
npm run test:e2e:integration
```

### Smoke-Check gegen eine laufende Instanz
```bash
# Gegen ein Produktions-/Staging-Deployment (prüft u. a. 404 auf /q/*)
./scripts/smoke-check.sh https://training.example.de
```

*(Hinweis: Im lokalen `%dev`-Profil sind `/q/openapi` und `/q/swagger-ui` absichtlich aktiv (`200`), sodass der `/q/*`-Blocktest im Smoke-Check für `%prod`/Staging gedacht ist.)*
