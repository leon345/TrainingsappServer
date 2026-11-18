-- V3: Ausstehende Registrierungen mit Ablaufzeit (S-04, E18).
-- Zwischen Registrierungsoptionen und Abschluss ist fuer einen Benutzernamen eine BenutzerId
-- (= User-Handle) vorgesehen. id ist diese vorgesehene BenutzerId. Eintraege verfallen nach
-- gueltig_bis (TTL: trainingsapp.passkey.ausstehende-registrierung-ttl); abgelaufene Eintraege
-- entfernt die Anwendung bei jeder neuen Reservierung.
CREATE TABLE ausstehende_registrierung (
    id           uuid                     PRIMARY KEY,
    benutzername varchar(64)              NOT NULL,
    gueltig_bis  timestamp with time zone NOT NULL
);
CREATE UNIQUE INDEX ausstehende_registrierung_benutzername_uk
    ON ausstehende_registrierung (lower(benutzername));
CREATE INDEX ausstehende_registrierung_gueltig_bis_idx ON ausstehende_registrierung (gueltig_bis);
