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
| `%dev` | Quarkus Dev Services (PostgreSQL-Container, Image `postgres:18`) | stellt Dev Services |
| `%test` | Dev Services, nur fuer Datenbank-Tests (Testprofil `MitDatenbank`) | stellt Dev Services |
| prod | aus Umgebungsvariablen | `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` |

- `application.properties` enthaelt in keinem Profil URL, Benutzer oder Passwort. Das prueft
  `ProdProfilZugangsdatenTest`.
- Flyway wendet die Migrationen beim Start an (`migrate-at-start`).
- Ohne JDBC-URL wuerde Quarkus die Datasource still abschalten, Flyway die Migration ueberspringen
  und die Anwendung ohne Datenbank starten. Deshalb bricht `infrastructure.persistenz.DatenbankPflichtPruefung`
  den Start dann ab ("Datasource '<default>' was deactivated automatically because its URL is not set").
  Abschaltbar nur ueber `trainingsapp.datenbank.pflicht=false`, gesetzt allein in `%test`
  fuer Tests ohne Datenbankbezug.

### Dev Services (Standard)

Voraussetzung: Docker oder Podman, nutzbar fuer den eigenen Benutzer (unter Linux z. B. Mitglied
der Gruppe `docker`, danach neu anmelden). `mvn quarkus:dev` startet PostgreSQL automatisch.
Ohne Container-Laufzeit startet die Anwendung im Dev-Modus nicht.

### Alternative: lokale PostgreSQL

Ohne Docker/Podman eine lokale PostgreSQL (ab Version 14) per Umgebungsvariablen angeben,
z. B. fuer den Dev-Modus:

```bash
# einmalig als PostgreSQL-Superuser
psql -c "CREATE ROLE trainingsapp LOGIN PASSWORD 'trainingsapp';"
psql -c "CREATE DATABASE trainingsapp OWNER trainingsapp;"

export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/trainingsapp
export QUARKUS_DATASOURCE_USERNAME=trainingsapp
export QUARKUS_DATASOURCE_PASSWORD=trainingsapp
mvn quarkus:dev
```

Fuer Tests ist das moeglich, aber nur mit einer eigenen, leeren Testdatenbank: Das Testprofil
`MitDatenbank` leert die Datenbank vor jedem Lauf (`clean-at-start`). Dev Services startet dabei
trotzdem einen Container, sofern Docker verfuegbar ist; die Variablen haben Vorrang.

## Tests

- Datenbank-Tests tragen `@DatenbankErforderlich` und `@TestProfile(MitDatenbank.class)`.
  Das Profil startet PostgreSQL ueber Dev Services, leert die Datenbank, wendet alle Migrationen
  an und laesst Hibernate das Schema validieren. Zusaetzlich wird `src/test/resources/db/test-migration`
  geladen. Dort liegen nur Tabellen fuer Tests, als wiederholbare Migration `R__...`, damit sie nicht
  mit den Versionsnummern kollidieren.
- Quarkus-Tests ohne Datenbankbezug laufen ohne Datenbank und ohne Docker
  (`%test`: Dev Services, Flyway und Hibernate ORM aus).
- Schalter `datenbank.tests` (Beschluss E19):
  - nicht gesetzt (Standard): Datenbank-Tests sind Pflicht. Ohne Docker bzw. Datenbank schlaegt
    `mvn package` fehl.
  - `-Ddatenbank.tests=aus`: Datenbank-Tests werden uebersprungen (in Surefire als `Skipped`),
    mit WARN-Meldung im Log. Nur lokal zulaessig; eine CI darf `aus` nie setzen.
  - jeder andere Wert ist ein Fehler.
