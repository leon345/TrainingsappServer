package de.trainingsapp.api.v1.dsgvo;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.dsgvo.DsgvoService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkt zur sofortigen, vollständigen Kontolöschung unter /v1/konto.
 */
@Path(ApiVersion.V1 + "/konto")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "DSGVO", description = "Datenexport und Kontolöschung nach DSGVO")
public class KontoResource {

    private final DsgvoService dsgvoService;

    public KontoResource(DsgvoService dsgvoService) {
        this.dsgvoService = dsgvoService;
    }

    @DELETE
    @Operation(
            operationId = "loescheKontoV1",
            summary = "Eigenes Benutzerkonto vollständig löschen",
            description = "Löscht Konto, Passkeys, Timer-Einstellung, Workouts, Übungen, Sätze, Einladungen und ausstehende Registrierungen restlos in einer Transaktion und beendet sofort die aktive Sitzung."
    )
    @APIResponse(
            responseCode = "204",
            description = "Konto vollständig gelöscht und Sitzung beendet"
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response loeschen() {
        dsgvoService.loescheKontoFuerAktuellenBenutzer();
        return Response.noContent().build();
    }
}
