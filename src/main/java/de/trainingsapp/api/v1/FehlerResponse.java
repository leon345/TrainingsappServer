package de.trainingsapp.api.v1;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "FehlerV1")
public record FehlerResponse(String code, String meldung) {
}
