package de.trainingsapp.infrastructure.http;

/**
 * Reihenfolge der eigenen Vert.x-Filter (hoeher = frueher). Quarkus Security laeuft mit 400 (Host),
 * 300 (CORS), 200 (Authentifizierung) und 100 (Autorisierung); alle eigenen Filter liegen davor.
 */
final class FilterPrioritaeten {

    /** E16: 404 fuer /q/* und /.well-known/webauthn im Prod-Profil, vor allem anderen. */
    static final int Q_SPERRE = 10_000;
    /** E15: Origin-Pruefung fuer schreibende Requests. */
    static final int ORIGIN_PRUEFUNG = 9_000;
    /** E17: Rate-Limit je IP. */
    static final int RATE_LIMIT = 8_000;
    /** E14: Cookie-Attribute; registriert nur einen Headers-End-Handler. */
    static final int COOKIE_HAERTUNG = 7_000;

    private FilterPrioritaeten() {
    }
}
