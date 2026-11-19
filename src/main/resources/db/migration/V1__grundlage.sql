-- V1: Datenbankgrundlage der Trainingsapp (PostgreSQL)

CREATE TABLE IF NOT EXISTS schema_meta (
    schluessel      VARCHAR(100) PRIMARY KEY,
    wert            VARCHAR(255) NOT NULL,
    aktualisiert_am TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_meta (schluessel, wert)
VALUES ('schema_version', '1')
ON CONFLICT (schluessel) DO NOTHING;
