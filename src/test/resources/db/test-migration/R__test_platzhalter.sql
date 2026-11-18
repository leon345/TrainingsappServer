-- Nur fuer Tests (Ort db/test-migration ist nur im %test-Profil aktiv).
-- Wiederholbare Migration, damit sie nie mit Versionsnummern der echten Migrationen kollidiert.
CREATE TABLE IF NOT EXISTS platzhalter (
    id   uuid         PRIMARY KEY,
    name varchar(100) NOT NULL
);
