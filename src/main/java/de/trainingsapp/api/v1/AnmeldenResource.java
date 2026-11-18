package de.trainingsapp.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Unauthentifiziert (E12): Anmeldung mit einem beliebigen Passkey des Benutzers. */
@Path(ApiVersion.V1 + "/anmelden")
@Tag(name = "Authentifizierung")
@Produces(MediaType.APPLICATION_JSON)
public class AnmeldenResource {

    private final PasskeyService service;

    public AnmeldenResource(PasskeyService service) {
        this.service = service;
    }

    @POST
    @Path("/optionen")
    @Operation(operationId = "startAnmeldungV1", summary = "Anmeldung starten",
            description = "Liefert PublicKeyCredentialRequestOptions (ohne Benutzername, discoverable) "
                    + "und setzt das Challenge-Cookie.")
    @APIResponse(responseCode = "200", description = "Optionen fuer navigator.credentials.get()")
    public String optionen() {
        return service.anmeldungStarten();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "anmeldenV1", summary = "Anmeldung abschliessen",
            description = "Prueft die Signatur des Passkeys und setzt das Sitzungs-Cookie.")
    @APIResponse(responseCode = "204", description = "Angemeldet")
    @APIResponse(responseCode = "400", description = "Passkey ungueltig, unbekannt oder Challenge fehlt")
    public void anmelden(JsonNode credential) {
        if (credential == null) {
            throw new BadRequestException("credential fehlt");
        }
        service.anmeldungAbschliessen(credential.toString());
    }
}
