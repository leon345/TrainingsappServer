package de.trainingsapp.api.v1.workout;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.workout.WorkoutEinladungAnsicht;
import de.trainingsapp.application.workout.WorkoutEinladungService;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.WorkoutId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte zur Verwaltung von Einladungen an eigenen Workouts unter /v1/workouts/{id}/einladungen.
 */
@Path(ApiVersion.V1 + "/workouts/{id}/einladungen")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Workout-Einladungen", description = "Einladen und Entfernen von Benutzern an eigenen Workouts")
public class WorkoutEinladungResource {

    private final WorkoutEinladungService einladungService;

    public WorkoutEinladungResource(WorkoutEinladungService einladungService) {
        this.einladungService = einladungService;
    }

    @GET
    @PermissionsAllowed("workout:bearbeiten")
    @Operation(
            operationId = "listeWorkoutEinladungenV1",
            summary = "Einladungen eines eigenen Workouts auflisten",
            description = "Liefert alle eingeladenen Benutzer eines eigenen Workouts. Nur für den Besitzer zugänglich."
    )
    @APIResponse(
            responseCode = "200",
            description = "Einladungen erfolgreich geladen",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = WorkoutEinladungResponse[].class)
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Workout nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public List<WorkoutEinladungResponse> auflisten(@PathParam("id") UUID id) {
        return einladungService.listeFuerWorkoutFuerAktuellenBenutzer(new WorkoutId(id)).stream()
                .map(WorkoutEinladungResponse::ausAnsicht)
                .toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("workout:bearbeiten")
    @Operation(
            operationId = "erstelleWorkoutEinladungV1",
            summary = "Benutzer zu einem eigenen Workout einladen",
            description = "Lädt einen Benutzer per exaktem Benutzernamen zum Lesen eines eigenen Workouts ein."
    )
    @APIResponse(
            responseCode = "201",
            description = "Einladung erfolgreich erstellt",
            headers = @Header(
                    name = "Location",
                    description = "Pfad zur neu erstellten Einladung",
                    schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING)
            ),
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkoutEinladungResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültiger Benutzername oder Selbsteinladung",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Workout oder Benutzer nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Benutzer wurde bereits zu diesem Workout eingeladen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response erstellen(
            @PathParam("id") UUID id,
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            WorkoutEinladungErstellenRequest request
    ) {
        WorkoutEinladungAnsicht erstellt = einladungService.einladenFuerAktuellenBenutzer(
                new WorkoutId(id),
                request.benutzername()
        );
        URI location = URI.create(
                ApiVersion.V1 + "/workouts/" + id + "/einladungen/" + erstellt.einladung().eingeladenerId().alsText()
        );
        return Response.created(location)
                .entity(WorkoutEinladungResponse.ausAnsicht(erstellt))
                .build();
    }

    @DELETE
    @Path("/{eingeladenerId}")
    @PermissionsAllowed("workout:bearbeiten")
    @Operation(
            operationId = "loescheWorkoutEinladungV1",
            summary = "Einladung an einem eigenen Workout entfernen",
            description = "Entfernt die Einladung eines Benutzers an einem eigenen Workout. Nur für den Besitzer zugänglich."
    )
    @APIResponse(responseCode = "204", description = "Einladung erfolgreich entfernt")
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Workout oder Einladung nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response loeschen(
            @PathParam("id") UUID id,
            @PathParam("eingeladenerId") UUID eingeladenerId
    ) {
        einladungService.entfernenFuerAktuellenBenutzer(new WorkoutId(id), new BenutzerId(eingeladenerId));
        return Response.noContent().build();
    }
}
