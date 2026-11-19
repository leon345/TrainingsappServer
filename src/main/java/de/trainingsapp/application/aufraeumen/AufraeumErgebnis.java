package de.trainingsapp.application.aufraeumen;

/**
 * Ergebnis eines Aufräumlaufs (ausschließlich Zähler ohne Personenbezug).
 */
public record AufraeumErgebnis(
        long geloeschteRegistrierungen,
        int geloeschteKontenOhnePasskey
) {
}
