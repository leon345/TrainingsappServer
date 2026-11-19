package de.trainingsapp.api.v1;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * OpenAPI-Metadaten für die Trainingsapp REST-API.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Trainingsapp API",
                version = "1.0.0",
                description = "Versionierte REST-API der Trainingsapp (/v1)"
        )
)
public class OpenApiKonfiguration extends Application {
}
