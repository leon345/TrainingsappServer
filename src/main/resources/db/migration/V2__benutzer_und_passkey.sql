-- V2: Benutzer und ihre Passkeys (S-04, E9, E18, E28, E29).
-- Darf nur bis zum ersten Merge nach integration/server geaendert werden (Council, E28/E29).

-- Benutzer. id ist die BenutzerId (UUID) und zugleich der WebAuthn-User-Handle (16 Rohbytes).
-- benutzername ist ein Pseudonym (E17), gespeichert nur in Kleinschreibung (E28); der CHECK
-- enthaelt dieselbe Regex wie domain.benutzer.Benutzername.
-- letzte_passkey_nummer: zuletzt vergebenes n fuer die automatische Bezeichnung "Passkey n" (E29);
-- steigt nur, damit geloeschte oder umbenannte Nummern nie wiederverwendet werden.
CREATE TABLE benutzer (
    id                    uuid        PRIMARY KEY,
    benutzername          varchar(32) NOT NULL,
    letzte_passkey_nummer integer     NOT NULL DEFAULT 0 CHECK (letzte_passkey_nummer >= 0),
    CONSTRAINT benutzer_benutzername_uk UNIQUE (benutzername),
    CONSTRAINT benutzer_benutzername_ck
        CHECK (benutzername ~ '^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$')
);

-- Passkeys (WebAuthn-Credentials): die Daten fuer eine Anmeldung und fuer die Verwaltung (E29).
--   id                    technischer Schluessel (Konvention: uuid, vergeben von der Anwendung)
--   credential_id         WebAuthn-Credential-ID, Base64URL ohne Padding (bis 1023 Byte roh), global eindeutig
--   bezeichnung           sichtbare Bezeichnung, NFC, 1 bis 50 Zeichen; automatisch "Passkey n"
--   public_key            oeffentlicher Schluessel, X.509-kodiert
--   algorithmus           COSE-Algorithmus (z. B. -7 = ES256, -257 = RS256, -8 = EdDSA)
--   zaehler               Signaturzaehler des Authenticators (0 bei vielen synchronisierten Passkeys)
--   erstellt_am           Tag der Registrierung (UTC)
--   zuletzt_verwendet_am  Tag der letzten Anmeldung (UTC), hoechstens einmal pro Tag geschrieben
-- Bewusst keine IP, kein User-Agent, keine Geraete- oder Attestierungsdaten (kein AAGUID), E29.
-- Loeschen des Benutzers loescht seine Passkeys mit (Kontoloeschung, E7).
CREATE TABLE passkey (
    id                   uuid        PRIMARY KEY,
    credential_id        text        NOT NULL,
    benutzer_id          uuid        NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    bezeichnung          varchar(50) NOT NULL CHECK (char_length(bezeichnung) BETWEEN 1 AND 50),
    public_key           bytea       NOT NULL,
    algorithmus          bigint      NOT NULL,
    zaehler              bigint      NOT NULL CHECK (zaehler >= 0),
    erstellt_am          date        NOT NULL,
    zuletzt_verwendet_am date,
    CONSTRAINT passkey_credential_id_uk UNIQUE (credential_id)
);
CREATE INDEX passkey_benutzer_id_idx ON passkey (benutzer_id);
