# Erweiterungsschnittstelle: Interner `WorkoutImport`-Port

Dieses Dokument beschreibt den internen Erweiterungsport `de.trainingsapp.application.erweiterung.WorkoutImport` (Nutzerentscheid B / E22) für künftige Trainingsdaten-Importe (z. B. Apple Health / HealthKit oder andere Wearable-Quellen).

Im aktuellen Projektumfang gibt es **keinen** öffentlichen Import-REST-Endpunkt und **keinen** Apple-Health-Adapter. Der Port stellt ausschließlich die saubere fachliche und persistente Grundlage innerhalb der Application-Schicht bereit.

---

## 1. Port-Vertrag (`application.erweiterung.WorkoutImport`)

```java
Workout importiere(
    BenutzerId ownerId,
    Herkunft herkunft,
    String externeId,
    WorkoutImportDaten daten
);
```

### Verbindliche Invarianten

1. **Externe Herkunft & ID Pflicht:**
   - `herkunft` darf nicht `null` und nicht `Herkunft.MANUELL` sein (`UNGUELTIGE_HERKUNFT`).
   - `externeId` ist Pflicht (getrimmt `1–200` Zeichen, `UNGUELTIGE_EXTERNE_ID`).
   - Manuelle Workouts über `/v1/workouts` tragen stets `herkunft = MANUELL` und `externeId = null`.
2. **Idempotentes Upsert über `(ownerId, herkunft, externeId)`:**
   - Existiert für den Benutzer noch kein Workout mit derselben Kombination aus `(ownerId, herkunft, externeId)`, wird ein neues Workout mit `version = 0` angelegt.
   - Existiert bereits ein passender Eintrag, wird dieses Workout aktualisiert (inkl. Erhöhung von `version` und Ersetzen der Übungen/Sätze), statt ein Duplikat zu erzeugen.
   - Auf Datenbankebene sichert der eindeutige Teilindex `uq_workout_herkunft_extern ON workout (owner_id, herkunft, externe_id) WHERE externe_id IS NOT NULL` die Eindeutigkeit auch unter Nebenläufigkeit ab.
3. **Sichtbarkeit immer `PRIVAT`:**
   - `WorkoutImportDaten` enthält bewusst kein `sichtbarkeit`-Feld.
   - Sowohl beim erstmaligen Import als auch bei jedem idempotenten Folge-Import wird `sichtbarkeit = Sichtbarkeit.PRIVAT` erzwungen.
   - Importierte Trainingsdaten werden niemals automatisch öffentlich sichtbar geschaltet.
4. **Identische Domain-Validierung wie manuelle Eingaben:**
   - Alle Zeitdauern werden als ganze Sekunden (`integer`) geprüft (`dauerSekunden` 1–86400, `pauseSekunden` 0–86400).
   - Titel (1–100 Zeichen), Notiz (max. 500 Zeichen), Übungsname (1–100 Zeichen), optionale Progressionsvariante (max. 100 Zeichen) sowie die Satz-Regel (mindestens `wiederholungen` oder `dauerSekunden`) gelten unverändert.

---

## 2. Datenschutz & Einwilligungspflicht (DSGVO Art. 9)

Trainings-, Fitness- und insbesondere aus externen Gesundheitsplattformen (wie Apple Health / HealthKit) importierte Daten können Gesundheitsdaten im Sinne von **Art. 9 DSGVO** darstellen.

Daher gilt verbindlich für jede künftige Anbindung eines Adapters an `WorkoutImport`:

- **Keine Anbindung ohne eigene, ausdrückliche Einwilligung:** Die allgemeine Konto-Einwilligung bei der Registrierung deckt externe Datenimporte **nicht** ab. Vor der Aktivierung einer externen Quelle muss eine gesonderte, granulare und jederzeit widerrufliche Einwilligung des Benutzers eingeholt und protokolliert werden.
- **Datenminimierung:** Es werden ausschließlich fachlich benötigte Workout-Daten (Typ, Titel, Startzeit, Dauer, Übungen/Sätze) importiert — keine Rohdaten wie Pulsverläufe, Standort-Tracks oder medizinische Kennzahlen ohne eigene fachliche Grundlage.
- **Standardmäßig privat:** Alle importierten Daten bleiben `PRIVAT` und sind ausschließlich für den Eigentümer sichtbar (`ZugriffsPolicy`; auch `ADMIN` hat keinen Zugriff).

---

## 3. Neue `Herkunft`-Werte ergänzen

Um künftig eine weitere Datenquelle (z. B. `GARMIN` oder `HEALTH_CONNECT`) anzubinden:

1. **Enum erweitern:** Neuen Wert in `de.trainingsapp.domain.workout.Herkunft` ergänzen.
2. **Neue Flyway-Migration anlegen** (bestehende Migrationen `V1`–`V3` niemals ändern!):
   ```sql
   -- Beispiel: V5__workout_herkunft_erweitern.sql
   ALTER TABLE workout DROP CONSTRAINT chk_workout_herkunft;
   ALTER TABLE workout ADD CONSTRAINT chk_workout_herkunft
       CHECK (herkunft IN ('MANUELL', 'APPLE_HEALTH', 'NEUE_QUELLE'));
   ```
3. **Adapter in `infrastructure` implementieren:** Der neue Adapter (z. B. `infrastructure.importquelle.*`) prüft die gesonderte Einwilligung, transformiert das Quellformat in `WorkoutImportDaten` und ruft ausschließlich den Port `WorkoutImport.importiere(...)` auf.
