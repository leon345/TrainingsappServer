package de.trainingsapp.api.v1.workout;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.workout.WorkoutService;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutTyp;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte zur Verwaltung von Kraft- und Calisthenics-Workouts unter /v1/workouts.
 */
@Path(ApiVersion.V1 + "/workouts")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Workouts", description = "Verwaltung von Kraft- und Calisthenics-Workouts")
public class WorkoutResource {

    private final WorkoutService workoutService;

    public WorkoutResource(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @GET
    @Operation(
            operationId = "listeWorkoutsV1",
            summary = "Eigene Workouts auflisten",
            description = "Liefert alle eigenen Workouts des angemeldeten Benutzers absteigend nach Startzeit, optional gefiltert nach Typ."
    )
    @APIResponse(
            responseCode = "200",
            description = "Workouts erfolgreich geladen",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = WorkoutResponse[].class)
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public List<WorkoutResponse> auflisten(
            @Parameter(description = "Optionaler Filter nach Workout-Typ (KRAFT oder CALISTHENICS)")
            @QueryParam("typ")
            WorkoutTyp typ
    ) {
        return workoutService.listeEigeneFuerAktuellenBenutzer(typ).stream()
                .map(WorkoutResponse::ausDomain)
                .toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "erstelleWorkoutV1",
            summary = "Neues Workout anlegen",
            description = "Erstellt ein neues manuelles Kraft- oder Calisthenics-Workout (Standardsichtbarkeit PRIVAT)."
    )
    @APIResponse(
            responseCode = "201",
            description = "Workout erfolgreich erstellt",
            headers = @Header(
                    name = "Location",
                    description = "Pfad zum neu erstellten Workout",
                    schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING)
            ),
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkoutResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Eingabedaten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response erstellen(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            WorkoutErstellenRequest request
    ) {
        Workout erstellt = workoutService.erstelleFuerAktuellenBenutzer(request.zuApplication());
        URI location = URI.create(ApiVersion.V1 + "/workouts/" + erstellt.id().alsText());
        return Response.created(location)
                .entity(WorkoutResponse.ausDomain(erstellt))
                .build();
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("workout:lesen")
    @Operation(
            operationId = "getWorkoutV1",
            summary = "Workout-Details abrufen",
            description = "Liefert ein einzelnes Workout inkl. aller Übungen und Sätze. Fremde private Workouts liefern 404."
    )
    @APIResponse(
            responseCode = "200",
            description = "Workout erfolgreich geladen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkoutResponse.class))
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
    public WorkoutResponse abrufen(@PathParam("id") UUID id) {
        Workout workout = workoutService.leseFuerAktuellenBenutzer(new WorkoutId(id));
        return WorkoutResponse.ausDomain(workout);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("workout:bearbeiten")
    @Operation(
            operationId = "aktualisiereWorkoutV1",
            summary = "Eigenes Workout aktualisieren",
            description = "Aktualisiert ein eigenes Workout vollständig unter Prüfung der optimistischen Sperrversion."
    )
    @APIResponse(
            responseCode = "200",
            description = "Workout erfolgreich aktualisiert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkoutResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Eingabedaten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
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
    @APIResponse(
            responseCode = "409",
            description = "Versionskonflikt bei optimistischer Sperre",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public WorkoutResponse aktualisieren(
            @PathParam("id") UUID id,
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            WorkoutAktualisierenRequest request
    ) {
        Workout aktualisiert = workoutService.aktualisiereFuerAktuellenBenutzer(
                new WorkoutId(id),
                request.version(),
                request.zuApplication()
        );
        return WorkoutResponse.ausDomain(aktualisiert);
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("workout:bearbeiten")
    @Operation(
            operationId = "loescheWorkoutV1",
            summary = "Eigenes Workout löschen",
            description = "Löscht ein eigenes Workout samt zugehörigen Übungen und Sätzen."
    )
    @APIResponse(responseCode = "204", description = "Workout erfolgreich gelöscht")
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
    public Response loeschen(@PathParam("id") UUID id) {
        workoutService.loescheFuerAktuellenBenutzer(new WorkoutId(id));
        return Response.noContent().build();
    }
}
