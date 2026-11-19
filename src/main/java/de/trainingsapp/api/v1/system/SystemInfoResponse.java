package de.trainingsapp.api.v1.system;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Antwort-DTO für Systeminformationen des Servers.
 */
@Schema(name = "SystemInfoV1", description = "Systeminformationen des Servers")
public record SystemInfoResponse(
        @Schema(
                description = "Name der Anwendung",
                examples = "trainingsapp-server",
                required = true
        )
        String name,
        @Schema(
                description = "Version der Anwendung",
                examples = "0.1.0-SNAPSHOT",
                required = true
        )
        String version
) {
}
