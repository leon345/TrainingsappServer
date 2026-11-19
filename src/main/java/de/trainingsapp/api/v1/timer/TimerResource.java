package de.trainingsapp.api.v1.timer;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.timer.TimerEinstellungAnsicht;
import de.trainingsapp.application.timer.TimerEinstellungService;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte für die synchronisierte Intervalltimer-Einstellung unter /v1/timer/einstellung.
 */
@Path(ApiVersion.V1 + "/timer/einstellung")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Timer", description = "Synchronisierte Intervalltimer-Einstellungen")
public class TimerResource {

    private final TimerEinstellungService timerEinstellungService;

    public TimerResource(TimerEinstellungService timerEinstellungService) {
        this.timerEinstellungService = timerEinstellungService;
    }

    @GET
    @Operation(
            operationId = "getTimerEinstellungV1",
            summary = "Intervalltimer-Einstellung abrufen",
            description = "Liefert die gespeicherte Timer-Einstellung des angemeldeten Benutzers oder den Standard (30 s Training, 10 s Pause, gespeichert=false)."
    )
    @APIResponse(
            responseCode = "200",
            description = "Timer-Einstellung erfolgreich geladen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TimerEinstellungResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public TimerEinstellungResponse abrufen() {
        TimerEinstellungAnsicht ansicht = timerEinstellungService.leseFuerAktuellenBenutzer();
        return TimerEinstellungResponse.ausAnsicht(ansicht);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "putTimerEinstellungV1",
            summary = "Intervalltimer-Einstellung speichern",
            description = "Validiert und speichert die Intervalltimer-Einstellung des angemeldeten Benutzers in ganzen Sekunden."
    )
    @APIResponse(
            responseCode = "200",
            description = "Timer-Einstellung erfolgreich gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TimerEinstellungResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Zeitdauern",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public TimerEinstellungResponse speichern(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            TimerEinstellungRequest request
    ) {
        TimerEinstellungAnsicht gespeichert = timerEinstellungService.speichereFuerAktuellenBenutzer(
                request.trainingSekunden(),
                request.pauseSekunden()
        );
        return TimerEinstellungResponse.ausAnsicht(gespeichert);
    }
}
