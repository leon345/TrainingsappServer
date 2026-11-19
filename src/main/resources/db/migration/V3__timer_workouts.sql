-- V3: Timer-Einstellungen, Workouts, Übungen und Sätze (PostgreSQL)

CREATE TABLE timer_einstellung (
    benutzer_id       UUID        PRIMARY KEY REFERENCES benutzer (id) ON DELETE CASCADE,
    training_sekunden INTEGER     NOT NULL,
    pause_sekunden    INTEGER     NOT NULL,
    geaendert_am      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_timer_training_sekunden CHECK (training_sekunden BETWEEN 1 AND 86400),
    CONSTRAINT chk_timer_pause_sekunden CHECK (pause_sekunden BETWEEN 0 AND 86400)
);

CREATE TABLE workout (
    id             UUID         PRIMARY KEY,
    owner_id       UUID         NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    typ            VARCHAR(32)  NOT NULL,
    titel          VARCHAR(100) NOT NULL,
    start_zeit     TIMESTAMPTZ  NOT NULL,
    dauer_sekunden INTEGER,
    notiz          VARCHAR(500),
    sichtbarkeit   VARCHAR(32)  NOT NULL DEFAULT 'PRIVAT',
    herkunft       VARCHAR(32)  NOT NULL DEFAULT 'MANUELL',
    externe_id     VARCHAR(200),
    erstellt_am    TIMESTAMPTZ  NOT NULL,
    geaendert_am   TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_workout_typ CHECK (typ IN ('KRAFT', 'CALISTHENICS')),
    CONSTRAINT chk_workout_titel CHECK (char_length(btrim(titel)) BETWEEN 1 AND 100),
    CONSTRAINT chk_workout_dauer_sekunden CHECK (dauer_sekunden IS NULL OR dauer_sekunden BETWEEN 1 AND 86400),
    CONSTRAINT chk_workout_notiz CHECK (notiz IS NULL OR char_length(notiz) <= 500),
    CONSTRAINT chk_workout_sichtbarkeit CHECK (sichtbarkeit IN ('PRIVAT', 'OEFFENTLICH')),
    CONSTRAINT chk_workout_herkunft CHECK (herkunft IN ('MANUELL', 'APPLE_HEALTH')),
    CONSTRAINT chk_workout_herkunft_externe_id CHECK (
        (herkunft = 'MANUELL' AND externe_id IS NULL)
        OR (herkunft <> 'MANUELL' AND externe_id IS NOT NULL AND char_length(btrim(externe_id)) BETWEEN 1 AND 200)
    ),
    CONSTRAINT chk_workout_version CHECK (version >= 0)
);

CREATE INDEX idx_workout_owner_start_zeit ON workout (owner_id, start_zeit DESC);
CREATE UNIQUE INDEX uq_workout_herkunft_extern
    ON workout (owner_id, herkunft, externe_id)
    WHERE externe_id IS NOT NULL;

CREATE TABLE uebung (
    id         UUID         PRIMARY KEY,
    workout_id UUID         NOT NULL REFERENCES workout (id) ON DELETE CASCADE,
    position   INTEGER      NOT NULL,
    name       VARCHAR(100) NOT NULL,
    variante   VARCHAR(100),
    CONSTRAINT chk_uebung_position CHECK (position >= 0),
    CONSTRAINT chk_uebung_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT chk_uebung_variante CHECK (variante IS NULL OR char_length(btrim(variante)) BETWEEN 1 AND 100)
);

CREATE INDEX idx_uebung_workout_id ON uebung (workout_id);

CREATE TABLE satz (
    id             UUID          PRIMARY KEY,
    uebung_id      UUID          NOT NULL REFERENCES uebung (id) ON DELETE CASCADE,
    position       INTEGER       NOT NULL,
    wiederholungen INTEGER,
    dauer_sekunden INTEGER,
    gewicht_kg     NUMERIC(6, 2),
    pause_sekunden INTEGER,
    CONSTRAINT chk_satz_position CHECK (position >= 0),
    CONSTRAINT chk_satz_wiederholungen CHECK (wiederholungen IS NULL OR wiederholungen BETWEEN 1 AND 10000),
    CONSTRAINT chk_satz_dauer_sekunden CHECK (dauer_sekunden IS NULL OR dauer_sekunden BETWEEN 1 AND 86400),
    CONSTRAINT chk_satz_gewicht_kg CHECK (gewicht_kg IS NULL OR gewicht_kg >= 0),
    CONSTRAINT chk_satz_pause_sekunden CHECK (pause_sekunden IS NULL OR pause_sekunden BETWEEN 0 AND 86400),
    CONSTRAINT chk_satz_leistung CHECK (wiederholungen IS NOT NULL OR dauer_sekunden IS NOT NULL)
);

CREATE INDEX idx_satz_uebung_id ON satz (uebung_id);
