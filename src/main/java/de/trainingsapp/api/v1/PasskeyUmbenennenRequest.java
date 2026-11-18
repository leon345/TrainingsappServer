package de.trainingsapp.api.v1;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PasskeyUmbenennenV1")
public record PasskeyUmbenennenRequest(
        @Schema(required = true, minLength = 1, maxLength = 50,
                description = "Neue Bezeichnung; Unicode erlaubt, wird nach NFC normalisiert und getrimmt, "
                        + "ohne Steuer- oder Formatzeichen")
        String bezeichnung) {
}
