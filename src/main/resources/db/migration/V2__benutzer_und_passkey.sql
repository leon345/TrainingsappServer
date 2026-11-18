-- V2: Benutzer und ihre Passkeys (S-04, E9, E18, E28, E29).
-- Darf nur bis zum ersten Merge nach integration/server geaendert werden (Council, E28/E29).

-- Benutzer. id ist die BenutzerId (UUID) und zugleich der WebAuthn-User-Handle (16 Rohbytes).
-- benutzername ist ein Pseudonym (E17), gespeichert nur in Kleinschreibung (E28); der CHECK
-- enthaelt dieselbe Regex wie domain.benutzer.Benutzername.
CREATE TABLE benutzer (
    id           uuid        PRIMARY KEY,
    benutzername varchar(32) NOT NULL,
    CONSTRAINT benutzer_benutzername_uk UNIQUE (benutzername),
    CONSTRAINT benutzer_benutzername_ck
        CHECK (benutzername ~ '^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$')
);

-- Passkeys (WebAuthn-Credentials). Genau die Daten, die fuer eine Anmeldung noetig sind.
--   id             technischer Schluessel (Konvention: uuid, vergeben von der Anwendung)
--   credential_id  WebAuthn-Credential-ID, Base64URL ohne Padding (bis 1023 Byte roh), global eindeutig
--   public_key     oeffentlicher Schluessel, X.509-kodiert
--   algorithmus    COSE-Algorithmus (z. B. -7 = ES256, -257 = RS256, -8 = EdDSA)
--   zaehler        Signaturzaehler des Authenticators (0 bei vielen synchronisierten Passkeys)
-- Loeschen des Benutzers loescht seine Passkeys mit (Kontoloeschung, E7).
CREATE TABLE passkey (
    id            uuid                     PRIMARY KEY,
    credential_id text                     NOT NULL,
    benutzer_id   uuid                     NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    aaguid        uuid,
    public_key    bytea                    NOT NULL,
    algorithmus   bigint                   NOT NULL,
    zaehler       bigint                   NOT NULL CHECK (zaehler >= 0),
    erstellt_am   timestamp with time zone NOT NULL,
    CONSTRAINT passkey_credential_id_uk UNIQUE (credential_id)
);
CREATE INDEX passkey_benutzer_id_idx ON passkey (benutzer_id);
