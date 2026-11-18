package de.trainingsapp.api.v1;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.application.passkey.PasskeyService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Beendet die Sitzung (E25: nur mit Anmeldung erreichbar). */
@Path(ApiVersion.V1 + "/abmelden")
@Authenticated
@Tag(name = "Authentifizierung")
public class AbmeldenResource {

    private final PasskeyService service;

    public AbmeldenResource(PasskeyService service) {
        this.service = service;
    }

    @POST
    @Operation(operationId = "abmeldenV1", summary = "Abmelden",
            description = "Beendet die Sitzung. Beim Cookie-Modell wird das Sitzungs-Cookie geloescht; "
                    + "ein vorher kopiertes Cookie bleibt bis zu seinem Ablauf gueltig (kein serverseitiger Widerruf).")
    @APIResponse(responseCode = "204", description = "Abgemeldet")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet")
    public void abmelden() {
        service.abmelden();
    }
}
