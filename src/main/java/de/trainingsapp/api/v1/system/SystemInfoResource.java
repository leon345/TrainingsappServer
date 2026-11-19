package de.trainingsapp.api.v1.system;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.system.SystemInfoService;
import de.trainingsapp.domain.system.SystemInfo;
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
 * Referenz-Endpunkt für Systeminformationen unter /v1/info.
 */
@Path(ApiVersion.V1 + "/info")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "System", description = "Systeminformationen des Servers")
public class SystemInfoResource {

    private final SystemInfoService systemInfoService;

    public SystemInfoResource(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @GET
    @Operation(
            operationId = "getSystemInfoV1",
            summary = "Systeminformationen abrufen",
            description = "Liefert den Anwendungsnamen und die aktuelle Serverversion für angemeldete Benutzer."
    )
    @APIResponse(
            responseCode = "200",
            description = "Systeminformationen erfolgreich geladen",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SystemInfoResponse.class)
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = FehlerResponse.class)
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Interner Serverfehler",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = FehlerResponse.class)
            )
    )
    public SystemInfoResponse getSystemInfo() {
        SystemInfo info = systemInfoService.lesen();
        return new SystemInfoResponse(info.name(), info.version());
    }
}
