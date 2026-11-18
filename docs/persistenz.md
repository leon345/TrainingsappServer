# Persistenz (Server)

PostgreSQL, Hibernate ORM mit Panache im Repository-Muster, Schema ausschliesslich ueber Flyway.

## Schichten

| Was | Wo |
|---|---|
| Domain-Objekte und Ports (Repository-Interfaces) | `domain.<kontext>` bzw. `application.<kontext>`, ohne Persistenz-Imports |
| JPA-Entities, Panache-Repositories, Mapper | nur in `infrastructure.<kontext>` |

- Ein Adapter implementiert den Port und `PanacheRepositoryBase<Entity, UUID>`.
  Nach aussen gibt er nur Domain-Objekte zurueck, nie Entities. Ein Mapper uebersetzt in beide Richtungen.
- Referenzbeispiel (nur in Testquellen, keine fachliche Tabelle):
  `domain.platzhalter.PlatzhalterRepository` -> `infrastructure.platzhalter.PanachePlatzhalterRepository`
  mit `PlatzhalterEntity` und `PlatzhalterMapper`.
- `architektur.PersistenzSchichtenTest` laesst den Build scheitern, wenn `domain` oder `application`
  Persistenz-Technik importieren (`jakarta.persistence`, Hibernate, Panache, Flyway, JDBC).

## Konventionen

- Migrationen: `src/main/resources/db/migration/V<Nummer>__<beschreibung_snake_case>.sql`,
  fortlaufend ohne Luecken (V1, V2, ...). Angewendete Migrationen werden nie geaendert.
- Schema `public`. Tabellen und Spalten in snake_case, Tabellen im Singular. Hibernate nutzt dafuer
  `CamelCaseToUnderscoresNamingStrategy` (`startZeit` -> `start_zeit`).
- Primaerschluessel: Spalte `id` vom Typ `uuid`. Die Anwendung vergibt die ID beim Erzeugen des
  Domain-Objekts (kein Datenbank-Generator).
- Zeitpunkte: `timestamp with time zone`, JDBC-Zeitzone UTC.
- Hibernate erzeugt kein Schema (`schema-management.strategy=validate`).

## Profile und Zugangsdaten

| Profil | Datenbank | Zugangsdaten |
|---|---|---|
| `%dev` | `jdbc:postgresql://localhost:5432/trainingsapp` | `trainingsapp` / `trainingsapp`, nur lokal |
| `%test` | `jdbc:postgresql://localhost:5432/trainingsapp_test` | `trainingsapp` / `trainingsapp`, nur lokal |
| prod | aus Umgebungsvariablen | `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` |

Im Prod-Profil enthaelt `application.properties` keine Zugangsdaten. Das prueft
`ProdProfilZugangsdatenTest`. Ohne die Variablen startet die Anwendung nicht.
Flyway wendet die Migrationen beim Start an (`migrate-at-start`).

Dev Services (PostgreSQL im Container) sind nicht eingerichtet, weil auf dem Dev-Rechner weder
Docker noch Podman verfuegbar war. Da in `%dev` und `%test` eine URL gesetzt ist, startet Quarkus
keine Dev Services. Jede Einstellung laesst sich per Umgebungsvariable ueberschreiben
(z. B. `QUARKUS_DATASOURCE_JDBC_URL`).

## Lokale Datenbank einrichten

```bash
# als PostgreSQL-Superuser (PostgreSQL ab Version 14)
psql -c "CREATE ROLE trainingsapp LOGIN PASSWORD 'trainingsapp';"
psql -c "CREATE DATABASE trainingsapp OWNER trainingsapp;"
psql -c "CREATE DATABASE trainingsapp_test OWNER trainingsapp;"
```

## Tests

- `mvn package` laeuft auch ohne Datenbank. Quarkus-Tests ohne Datenbankbezug starten dann
  ohne Migration (`%test.quarkus.flyway.migrate-at-start=false`).
- Datenbank-Tests tragen `@DatenbankErforderlich` und `@TestProfile(MitDatenbank.class)`.
  Das Profil leert die Testdatenbank, wendet alle Migrationen an und validiert das Schema.
  Zusaetzlich wird `src/test/resources/db/test-migration` geladen. Dort liegen nur Tabellen
  fuer Tests, als wiederholbare Migration `R__...`, damit sie nicht mit den Versionsnummern kollidieren.
- Steuerung ueber `-Ddatenbank.tests=...`:
  - nicht gesetzt: laufen nur, wenn die Test-Datenbank per TCP erreichbar ist, sonst werden sie
    uebersprungen (in der Surefire-Ausgabe als `Skipped`),
  - `pflicht`: laufen immer und schlagen ohne Datenbank fehl (fuer CI),
  - `aus`: werden immer uebersprungen.
