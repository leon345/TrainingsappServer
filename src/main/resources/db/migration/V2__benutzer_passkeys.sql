-- V2: Benutzer, Passkeys und ausstehende Registrierungen (PostgreSQL)

CREATE TABLE benutzer (
    id                      UUID         PRIMARY KEY,
    benutzername            VARCHAR(32)  NOT NULL,
    rolle                   VARCHAR(20)  NOT NULL,
    erstellt_am             TIMESTAMPTZ  NOT NULL,
    einwilligung_am         TIMESTAMPTZ  NOT NULL,
    naechste_passkey_nummer INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_benutzer_benutzername UNIQUE (benutzername),
    CONSTRAINT chk_benutzer_benutzername CHECK (
        benutzername ~ '^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$'
    ),
    CONSTRAINT chk_benutzer_rolle CHECK (rolle IN ('USER', 'ADMIN')),
    CONSTRAINT chk_benutzer_naechste_passkey_nummer CHECK (naechste_passkey_nummer >= 1)
);

CREATE TABLE passkey (
    id                   UUID         PRIMARY KEY,
    benutzer_id          UUID         NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    credential_id        VARCHAR(512) NOT NULL,
    public_key           BYTEA        NOT NULL,
    public_key_algorithm BIGINT       NOT NULL,
    sign_count           BIGINT       NOT NULL DEFAULT 0,
    aaguid               UUID         NOT NULL,
    bezeichnung          VARCHAR(50)  NOT NULL,
    erstellt_am          DATE         NOT NULL,
    zuletzt_verwendet_am DATE,
    CONSTRAINT uq_passkey_credential_id UNIQUE (credential_id),
    CONSTRAINT chk_passkey_sign_count CHECK (sign_count >= 0),
    CONSTRAINT chk_passkey_bezeichnung CHECK (char_length(btrim(bezeichnung)) BETWEEN 1 AND 50)
);

CREATE INDEX idx_passkey_benutzer_id ON passkey (benutzer_id);

CREATE TABLE ausstehende_registrierung (
    benutzer_id     UUID        PRIMARY KEY,
    benutzername    VARCHAR(32) NOT NULL,
    erstellt_am     TIMESTAMPTZ NOT NULL,
    einwilligung_am TIMESTAMPTZ NOT NULL,
    laeuft_ab_am    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ausstehende_registrierung_benutzername UNIQUE (benutzername),
    CONSTRAINT chk_ausstehende_registrierung_benutzername CHECK (
        benutzername ~ '^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$'
    ),
    CONSTRAINT chk_ausstehende_registrierung_zeitraum CHECK (laeuft_ab_am > erstellt_am)
);

CREATE INDEX idx_ausstehende_registrierung_laeuft_ab_am ON ausstehende_registrierung (laeuft_ab_am);
