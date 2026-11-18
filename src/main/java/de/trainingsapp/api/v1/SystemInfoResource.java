package de.trainingsapp.api.v1;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.application.systeminfo.SystemInfoService;
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
 * Beispielendpunkt der API v1.
 */
@Path(ApiVersion.V1 + "/info")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System", description = "Technische Informationen zur Anwendung")
public class SystemInfoResource {

    private final SystemInfoService service;

    public SystemInfoResource(SystemInfoService service) {
        this.service = service;
    }

    @GET
    @Operation(operationId = "getSystemInfoV1", summary = "Systeminformationen abfragen",
            description = "Liefert Name und Version der laufenden Anwendung.")
    @APIResponse(responseCode = "200", description = "Systeminformationen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SystemInfoResponse.class)))
    public SystemInfoResponse get() {
        return SystemInfoResponse.from(service.getSystemInfo());
    }
}
