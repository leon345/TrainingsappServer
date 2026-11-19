-- V4: Workout-Einladungen für geteilte Workouts und kaskadierende Kontolöschung (PostgreSQL)

CREATE TABLE workout_einladung (
    id              UUID        PRIMARY KEY,
    workout_id      UUID        NOT NULL REFERENCES workout (id) ON DELETE CASCADE,
    eingeladener_id UUID        NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    status          VARCHAR(32) NOT NULL DEFAULT 'AKTIV',
    erstellt_am     TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_workout_einladung_status CHECK (status IN ('AKTIV')),
    CONSTRAINT uq_workout_einladung_workout_eingeladener UNIQUE (workout_id, eingeladener_id)
);

CREATE INDEX idx_workout_einladung_eingeladener_erstellt_am
    ON workout_einladung (eingeladener_id, erstellt_am DESC);
