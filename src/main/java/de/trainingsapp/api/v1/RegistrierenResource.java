package de.trainingsapp.api.v1;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.application.passkey.PasskeyService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Unauthentifiziert (E12): neuen Benutzer mit erstem Passkey anlegen. */
@Path(ApiVersion.V1 + "/registrieren")
@Tag(name = "Authentifizierung")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistrierenResource {

    private final PasskeyService service;

    public RegistrierenResource(PasskeyService service) {
        this.service = service;
    }

    @POST
    @Path("/optionen")
    @Operation(operationId = "startRegistrierungV1", summary = "Registrierung starten",
            description = "Liefert PublicKeyCredentialCreationOptions und setzt das Challenge-Cookie.")
    @APIResponse(responseCode = "200", description = "Optionen fuer navigator.credentials.create()")
    @APIResponse(responseCode = "400", description = "Benutzername fehlt oder ungueltig (z. B. mit @)")
    @APIResponse(responseCode = "409", description = "Benutzername vergeben")
    public String optionen(RegistrierungStartenRequest request) {
        if (request == null || request.benutzername() == null || request.benutzername().isBlank()) {
            throw new BadRequestException("benutzername fehlt");
        }
        return service.registrierungStarten(request.benutzername());
    }

    @POST
    @Operation(operationId = "registrierenV1", summary = "Registrierung abschliessen",
            description = "Prueft den neuen Passkey, legt den Benutzer an und setzt das Sitzungs-Cookie.")
    @APIResponse(responseCode = "204", description = "Registriert und angemeldet")
    @APIResponse(responseCode = "400", description = "Passkey ungueltig oder Challenge fehlt")
    @APIResponse(responseCode = "409", description = "Benutzername vergeben")
    public void registrieren(RegistrierungAbschliessenRequest request) {
        if (request == null || request.benutzername() == null || request.credential() == null) {
            throw new BadRequestException("benutzername oder credential fehlt");
        }
        service.registrierungAbschliessen(request.benutzername(), request.credential().toString());
    }
}
