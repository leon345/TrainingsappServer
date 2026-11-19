# Datenschutz (DSGVO) & Technische und Organisatorische Maßnahmen (TOM)

Stand: 19.11.2026 | Geltungsbereich: `trainingsapp-server` (`de.trainingsapp`)

---

## 1. Verarbeitungsverzeichnis (Art. 30 DSGVO)

| Merkmal | Beschreibung |
| --- | --- |
| **Zweck der Verarbeitung** | Bereitstellung einer Webanwendung (PWA + REST-API) für Intervalltimer-Einstellungen und persönliches Workout-/Calisthenics-Tracking inkl. optionalem Teilen einzelner Workouts über Profil oder gezielte Einladung. |
| **Betroffene Personen** | Registrierte Nutzerinnen und Nutzer der Trainingsapp. |
| **Rechtsgrundlage** | Art. 6 Abs. 1 lit. a DSGVO (ausdrückliche Einwilligung bei der Registrierung, protokolliert als `einwilligung_am` in UTC) bzw. Art. 6 Abs. 1 lit. b DSGVO (Nutzungsverhältnis). Vor künftigen externen Gesundheitsdaten-Importen (z. B. Apple Health) ist zwingend eine gesonderte Einwilligung nach Art. 9 Abs. 2 lit. a DSGVO erforderlich (siehe [erweiterungen.md](erweiterungen.md)). |
| **Kategorien personenbezogener Daten** | **Kontodaten:** technische `BenutzerId` (UUID / 16-Byte-WebAuthn-User-Handle), selbst gewählter `benutzername` (3–32 Zeichen, keine Klarnamen- oder E-Mail-Pflicht), fachliche Rolle (`USER` / `ADMIN`), `erstellt_am`, `einwilligung_am`.<br>**Authentifizierungsdaten:** WebAuthn-Passkeys (`credential_id`, öffentlicher Schlüssel, Signaturzähler, selbst gewählte Bezeichnung, `erstellt_am` und `zuletzt_verwendet_am` als UTC-Datum ohne Uhrzeit). **Keine Passwörter**, keine biometrischen Daten auf dem Server.<br>**Trainingsdaten:** Intervalltimer-Einstellung (`trainingSekunden`, `pauseSekunden`), Workouts (`typ`, `titel`, `startZeit`, `dauerSekunden`, `notiz`, `sichtbarkeit`, `herkunft`), Übungen, Sätze.<br>**Freigabedaten:** Workout-Einladungen (`workout_id`, `eingeladener_id`, `status`, `erstellt_am`). |
| **Empfänger / Auftragsverarbeiter** | Betrieb durch den Betreiber auf eigener Infrastruktur in Deutschland (Strato, EU/EWR). Keine Weitergabe an Dritte, keine externen CDNs, keine Analytics- oder Tracking-Dienste. |
| **Drittlandübermittlung** | Keine (Hosting und PostgreSQL-Datenbank ausschließlich in Deutschland / EU). |

---

## 2. Technische und Organisatorische Maßnahmen (TOM, Art. 32 DSGVO)

### 2.1 Vertraulichkeit & Zugangskontrolle
- **Passwortlose Authentifizierung:** Anmeldung ausschließlich per FIDO2/WebAuthn-Passkeys (`quarkus-security-webauthn`, `ES256` & `RS256`, `userVerification=required`, `residentKey=required`, `attestation=none`).
- **Session-Schutz:** AES-GCM-verschlüsseltes Session-Cookie (`quarkus-credential`, Schlüssel via `SESSION_ENCRYPTION_KEY`) mit `SameSite=Strict` und `Secure` (automatisch aktiv hinter TLS-terminierendem Reverse Proxy via `quarkus.http.proxy.proxy-address-forwarding=true`).
  - *Bekannte Quarkus-`3.40.1.2`-Limitierung:* `quarkus-security-webauthn` setzt in Version `3.40.1.2` kein `HttpOnly`-Flag auf dem Session- und Challenge-Cookie (Details siehe [auth-erweiterung.md](auth-erweiterung.md)). Dies wird kompensiert durch `SameSite=Strict`, `Secure`, strikte Content-Security-Policy (`Content-Security-Policy: default-src 'self'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` und `Referrer-Policy: no-referrer`.
- **RBAC & ABAC (Datensparsamkeit & Mandantentrennung):**
  - Alle `/v1/*`-Endpunkte (inkl. unbekannter Pfade) außer Registrierung/Anmeldung erfordern eine gültige Sitzung (`401 FehlerV1`-JSON, kein Redirect).
  - Strikte Besitzerprüfung in `ZugriffsPolicy` / `ZugriffsPermissionChecker` (IDOR-Schutz; fremde/unbekannte Ressourcen liefern einheitlich `404` ohne ID-Aufzählung).
  - Die Rolle `ADMIN` dient ausschließlich Betriebsaufgaben und hat **keinen** Lese- oder Schreibzugriff auf fremde Passkeys, Timer oder Trainingsdaten (auch nicht auf fremde öffentliche Workouts).
- **Rate-Limiting & Missbrauchsschutz:** `@RateLimit` (`quarkus-smallrye-fault-tolerance`) auf Registrierungs-, Anmelde-, Such- und Profil-Endpunkten (`429 ZU_VIELE_ANFRAGEN`).
- **Härtung im Produktionsprofil (`%prod`):** OpenAPI, Swagger UI und `/q/webauthn/*` sind auf dem öffentlichen Port gesperrt (`404` ohne Body und ohne `Set-Cookie`); Health-Checks laufen ausschließlich auf dem internen Management-Port (`9000`).

### 2.2 Integrität, Transport- & Datensicherheit
- **Transportverschlüsselung:** Produktivbetrieb ausschließlich über HTTPS (TLS-Terminierung am Reverse Proxy mit `X-Forwarded-Proto: https` und empfohlenem HSTS).
- **Datensparsame Protokollierung:** Keine Passkey-Materialien, Cookies, Benutzernamen oder Trainingsinhalte in Anwendungs-Logs; bei unerwarteten Fehlern wird lediglich der Ausnahmetyp geloggt und `500 INTERNER_FEHLER` ohne Stacktrace ausgeliefert.
- **Datenbank-Integrität:** Flyway-Migrationen (`validate`-Modus in Hibernate), Fremdschlüssel mit `ON DELETE CASCADE`, optimistische Sperre (`version`) auf Workouts.

---

## 3. Aufbewahrungs- und Löschfristen

| Datenkategorie | Frist / Regel | Umsetzung |
| --- | --- | --- |
| **Ausstehende Registrierungen** | **10 Minuten** TTL | Automatische Löschung abgelaufener Einträge durch den täglichen `TaeglicherAufraeumJob` (`03:15 UTC`, `quarkus-scheduler`). |
| **Konten ohne Passkey** | **14 Tage** nach Erstellung | Automatische Löschung durch `TaeglicherAufraeumJob`. **Inaktive Konten mit mindestens einem Passkey werden nicht automatisch gelöscht.** |
| **Benutzerkonto & alle zugehörigen Daten** | **Sofort auf Nutzerwunsch** (Art. 17 DSGVO) | `DELETE /v1/konto` löscht das Konto inkl. Passkeys, Timer-Einstellung, Workouts, Übungen, Sätzen, aus- und eingehenden Einladungen sowie ausstehenden Registrierungen unmittelbar und macht die Sitzung sofort ungültig (`401`). |
| **Server- & Reverse-Proxy-Logs** | **Max. 14 Tage** Retention | Durch Betreiber-Konfiguration (Log-Rotation / Journald / Ingress) sicherzustellen; keine personenbezogenen Nutzdaten in Anwendungs-Logs. |
| **Datenbank-Backups** | **Max. 30 Tage** Retention | Verschlüsselte PostgreSQL-Backups im EU-/DE-Rechenzentrum des Betreibers; nach Ablauf von 30 Tagen automatische Löschung/Überschreibung. |

---

## 4. Betroffenenrechte (API-Unterstützung)

- **Auskunftsrecht (Art. 15 DSGVO) & Datenübertragbarkeit (Art. 20 DSGVO):** `GET /v1/dsgvo/export` liefert alle gespeicherten Daten des angemeldeten Benutzers (Kontodaten, Passkey-Metadaten ohne kryptografische Schlüssel, Timer-Einstellung, alle eigenen Workouts inkl. Übungen/Sätzen/Einladungen sowie erhaltene Einladungen) strukturiert als JSON.
- **Recht auf Löschung (Art. 17 DSGVO):** `DELETE /v1/konto` löscht alle Daten des angemeldeten Benutzers vollständig und kaskadierend (verifiziert durch `DsgvoIntegrationTest` direkt gegen PostgreSQL).
- **Recht auf Berichtigung (Art. 16 DSGVO):** Workouts, Timer-Einstellungen und Passkey-Bezeichnungen sind jederzeit über die jeweiligen `/v1`-Endpunkte bearbeitbar.

---

## 5. Offene rechtliche Pflichten des Betreibers (vor öffentlichem Produktivbetrieb)

Die folgenden organisatorischen und rechtlichen Pflichten liegen außerhalb der reinen Software-Implementierung und müssen vor einem öffentlichen Betrieb durch den Betreiber erfüllt werden:

1. **Impressum (§ 5 DDG):** Vollständige Anbieterkennzeichnung in der Anwendung verlinken.
2. **Datenschutzerklärung (Art. 13/14 DSGVO):** Konkrete Datenschutzerklärung mit Verantwortlichem, Zwecken, Rechtsgrundlagen, Speicherdauern (14 Tage Logs, 30 Tage Backups), Betroffenenrechten und Beschwerderecht bei einer Aufsichtsbehörde bereitstellen.
3. **Prüfung auf Gesundheitsdaten (Art. 9 DSGVO):** Juristische Bewertung, ob die konkret erfassten Trainings- und Calisthenics-Notizen bereits als Gesundheitsdaten einzuordnen sind; spätestens vor Aktivierung externer Importquellen (z. B. Apple Health) ist eine gesonderte, ausdrückliche Einwilligung zwingend.
4. **Auftragsverarbeitungsvertrag (AVV, Art. 28 DSGVO):** Abschluss und Ablage des AVV mit dem Hosting-Anbieter (**Strato**, Deutschland).
