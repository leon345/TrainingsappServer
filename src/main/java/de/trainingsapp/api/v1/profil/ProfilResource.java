package de.trainingsapp.api.v1.profil;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.api.v1.workout.WorkoutResponse;
import de.trainingsapp.application.profil.ProfilService;
import de.trainingsapp.domain.benutzer.BenutzerId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkt zum Lesen öffentlicher Profil-Workouts eines Benutzers (E4, E6).
 */
@Path(ApiVersion.V1 + "/profile")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Profile", description = "Öffentliche Workouts eines Benutzerprofils")
public class ProfilResource {

    private final ProfilService profilService;

    public ProfilResource(ProfilService profilService) {
        this.profilService = profilService;
    }

    @GET
    @Path("/{benutzerId}/workouts")
    @PermissionsAllowed("profil:lesen")
    @RateLimit(value = 30, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(
            operationId = "listeOeffentlicheProfilWorkoutsV1",
            summary = "Öffentliche Workouts eines Benutzerprofils abrufen",
            description = "Liefert ausschließlich öffentliche Workouts des angegebenen Benutzers für angemeldete USER bzw. den Besitzer selbst. Fremde ADMIN-Konten erhalten keinen Zugriff."
    )
    @APIResponse(
            responseCode = "200",
            description = "Öffentliche Workouts erfolgreich geladen",
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
    @APIResponse(
            responseCode = "403",
            description = "Kein Zugriff auf fremde Trainingsdaten",
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
    public List<WorkoutResponse> oeffentlicheWorkouts(@PathParam("benutzerId") UUID benutzerId) {
        return profilService.listeOeffentlicheWorkoutsFuerAktuellenBenutzer(new BenutzerId(benutzerId)).stream()
                .map(WorkoutResponse::ausDomain)
                .toList();
    }
}
