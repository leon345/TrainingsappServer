package de.trainingsapp.api.v1;

import de.trainingsapp.application.passkey.BenutzernameVergeben;
import de.trainingsapp.application.passkey.LetzterPasskey;
import de.trainingsapp.application.passkey.PasskeyNichtGefunden;
import de.trainingsapp.application.passkey.PasskeyPruefungFehlgeschlagen;
import de.trainingsapp.domain.benutzer.UngueltigerBenutzername;
import de.trainingsapp.domain.passkey.UngueltigeBezeichnung;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Abbildung der Passkey-Fehler auf HTTP-Statuscodes. */
public class PasskeyFehlerMapper {

    @ServerExceptionMapper
    public Response pruefung(PasskeyPruefungFehlgeschlagen e) {
        return fehler(Response.Status.BAD_REQUEST, "PASSKEY_UNGUELTIG", "Passkey konnte nicht geprueft werden");
    }

    @ServerExceptionMapper
    public Response ungueltigerName(UngueltigerBenutzername e) {
        return fehler(Response.Status.BAD_REQUEST, "BENUTZERNAME_UNGUELTIG", e.getMessage());
    }

    @ServerExceptionMapper
    public Response ungueltigeBezeichnung(UngueltigeBezeichnung e) {
        return fehler(Response.Status.BAD_REQUEST, "BEZEICHNUNG_UNGUELTIG", e.getMessage());
    }

    @ServerExceptionMapper
    public Response vergeben(BenutzernameVergeben e) {
        return fehler(Response.Status.CONFLICT, "BENUTZERNAME_VERGEBEN", "Benutzername ist bereits vergeben");
    }

    @ServerExceptionMapper
    public Response nichtGefunden(PasskeyNichtGefunden e) {
        return fehler(Response.Status.NOT_FOUND, "PASSKEY_NICHT_GEFUNDEN", "Passkey nicht gefunden");
    }

    @ServerExceptionMapper
    public Response letzter(LetzterPasskey e) {
        return fehler(Response.Status.CONFLICT, "LETZTER_PASSKEY",
                "Der letzte Passkey kann nicht geloescht werden");
    }

    /**
     * Ohne diesen Mapper antwortet der WebAuthn-Mechanismus der Extension auf fehlende
     * Anmeldung mit 302 auf {@code quarkus.webauthn.login-page} (Default /login.html).
     * Fuer eine JSON-API wollen wir 401.
     */
    @ServerExceptionMapper
    public Response nichtAngemeldet(UnauthorizedException e) {
        return fehler(Response.Status.UNAUTHORIZED, "NICHT_ANGEMELDET", "Anmeldung erforderlich");
    }

    private static Response fehler(Response.Status status, String code, String meldung) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new FehlerResponse(code, meldung)).build();
    }
}
