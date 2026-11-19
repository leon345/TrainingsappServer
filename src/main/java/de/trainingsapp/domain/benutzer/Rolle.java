package de.trainingsapp.domain.benutzer;

/**
 * Fachliche Benutzerrollen für RBAC.
 * ADMIN ist ausschließlich für Betriebsaufgaben vorgesehen und hat keinen Zugriff auf fremde Nutzerdaten.
 */
public enum Rolle {
    USER,
    ADMIN
}
