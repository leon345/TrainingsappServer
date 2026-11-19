package de.trainingsapp.api.v1.dsgvo;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.dsgvo.DsgvoService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkt für den synchronen DSGVO-Datenexport unter /v1/dsgvo/export.
 */
@Path(ApiVersion.V1 + "/dsgvo")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "DSGVO", description = "Datenexport und Kontolöschung nach DSGVO")
public class DsgvoResource {

    private final DsgvoService dsgvoService;

    public DsgvoResource(DsgvoService dsgvoService) {
        this.dsgvoService = dsgvoService;
    }

    @GET
    @Path("/export")
    @Operation(
            operationId = "exportiereDsgvoDatenV1",
            summary = "Vollständigen DSGVO-Datenexport abrufen",
            description = "Liefert synchron alle gespeicherten Daten des angemeldeten Benutzers (Profil, Passkey-Metadaten ohne kryptografische Schlüssel, Timer-Einstellung, eigene Workouts inkl. Übungen/Sätzen/Einladungen sowie erhaltene Einladungen) als JSON."
    )
    @APIResponse(
            responseCode = "200",
            description = "Datenexport erfolgreich erstellt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DsgvoExportResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public DsgvoExportResponse exportieren() {
        return DsgvoExportResponse.ausDaten(dsgvoService.exportiereDatenFuerAktuellenBenutzer());
    }
}
