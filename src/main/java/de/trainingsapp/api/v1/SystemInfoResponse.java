package de.trainingsapp.api.v1;

import de.trainingsapp.domain.systeminfo.SystemInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO der API v1. Domain-Objekte werden nie direkt serialisiert.
 */
@Schema(name = "SystemInfoV1", description = "Basisinformationen zur laufenden Anwendung")
public record SystemInfoResponse(
        @Schema(description = "Name der Anwendung", examples = "trainingsapp-server") String name,
        @Schema(description = "Version der Anwendung", examples = "0.1.0-SNAPSHOT") String version) {

    static SystemInfoResponse from(SystemInfo info) {
        return new SystemInfoResponse(info.name(), info.version());
    }
}
