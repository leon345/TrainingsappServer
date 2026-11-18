package de.trainingsapp.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "RegistrierungAbschliessenV1")
public record RegistrierungAbschliessenRequest(
        @Schema(required = true) String benutzername,
        @Schema(required = true, type = SchemaType.OBJECT,
                description = "PublicKeyCredential aus navigator.credentials.create() (JSON-Form)")
        JsonNode credential) {
}
