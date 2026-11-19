package de.trainingsapp.api.v1.benutzer;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.benutzer.BenutzerSucheService;
import de.trainingsapp.domain.benutzer.Benutzer;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkt zur exakten Benutzernamenssuche (ohne Teilstringsuche und ohne Benutzerliste).
 */
@Path(ApiVersion.V1 + "/benutzer")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Benutzer", description = "Exakte Benutzernamenssuche zur Vorbereitung von Einladungen")
public class BenutzerResource {

    private final BenutzerSucheService benutzerSucheService;

    public BenutzerResource(BenutzerSucheService benutzerSucheService) {
        this.benutzerSucheService = benutzerSucheService;
    }

    @GET
    @Path("/suche")
    @RateLimit(value = 30, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(
            operationId = "sucheBenutzerV1",
            summary = "Benutzer per exaktem Benutzernamen suchen",
            description = "Sucht genau nach dem normalisierten Benutzernamen (keine Teilstringsuche, keine Liste aller Benutzer)."
    )
    @APIResponse(
            responseCode = "200",
            description = "Benutzer gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BenutzerKurzResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültiger Benutzername",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Benutzer nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "429",
            description = "Rate-Limit überschritten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public BenutzerKurzResponse suche(
            @Parameter(description = "Exakter Benutzername (Groß-/Kleinschreibung wird normalisiert)", required = true)
            @QueryParam("name")
            String name
    ) {
        Benutzer gefunden = benutzerSucheService.sucheExaktFuerAktuellenBenutzer(name);
        return BenutzerKurzResponse.ausDomain(gefunden);
    }
}
