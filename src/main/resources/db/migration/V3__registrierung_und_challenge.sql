-- V3: Ausstehende Registrierungen mit Ablaufzeit (S-04, E18) und verbrauchte Challenges.
-- Darf nur bis zum ersten Merge nach integration/server geaendert werden (Council, E28/E29).

-- Zwischen Registrierungsoptionen und Abschluss ist fuer einen Benutzernamen eine BenutzerId
-- (= User-Handle) vorgesehen. id ist diese vorgesehene BenutzerId. Eintraege verfallen nach
-- gueltig_bis (TTL: trainingsapp.passkey.ausstehende-registrierung-ttl); abgelaufene Eintraege
-- entfernt die Anwendung bei jeder neuen Reservierung.
-- challenge: die zuletzt fuer diese Reservierung ausgegebene WebAuthn-Challenge (Base64URL). Der
-- Abschluss gelingt nur mit einer Antwort auf genau diese Challenge; so ist der Benutzername im
-- Abschluss an den Start gebunden.
CREATE TABLE ausstehende_registrierung (
    id           uuid                     PRIMARY KEY,
    benutzername varchar(32)              NOT NULL,
    gueltig_bis  timestamp with time zone NOT NULL,
    challenge    varchar(128),
    CONSTRAINT ausstehende_registrierung_benutzername_uk UNIQUE (benutzername),
    CONSTRAINT ausstehende_registrierung_benutzername_ck
        CHECK (benutzername ~ '^[a-z0-9](?:[a-z0-9]|[._-](?=[a-z0-9])){2,31}$')
);
CREATE INDEX ausstehende_registrierung_gueltig_bis_idx ON ausstehende_registrierung (gueltig_bis);

-- Verbrauchte WebAuthn-Challenges (nur SHA-256-Hash): jede Challenge ist einmal verwendbar.
-- Eintraege verfallen mit dem Challenge-Cookie (quarkus.webauthn.session-timeout).
CREATE TABLE verbrauchte_challenge (
    id             uuid                     PRIMARY KEY,
    challenge_hash bytea                    NOT NULL,
    gueltig_bis    timestamp with time zone NOT NULL,
    CONSTRAINT verbrauchte_challenge_hash_uk UNIQUE (challenge_hash)
);
CREATE INDEX verbrauchte_challenge_gueltig_bis_idx ON verbrauchte_challenge (gueltig_bis);
