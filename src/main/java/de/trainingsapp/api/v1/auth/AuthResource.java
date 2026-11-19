package de.trainingsapp.api.v1.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.passkey.PasskeyService;
import de.trainingsapp.domain.benutzer.Benutzer;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte für passwortlose Passkey-Registrierung, Anmeldung, Abmeldung und Benutzerprofil (/v1/ich).
 */
@Path(ApiVersion.V1)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentifizierung", description = "Passkey-Registrierung, Anmeldung, Abmeldung und aktueller Benutzer")
public class AuthResource {

    private final PasskeyService passkeyService;
    private final ObjectMapper objectMapper;

    public AuthResource(PasskeyService passkeyService, ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.objectMapper = objectMapper;
    }

    @POST
    @Path("/registrieren/optionen")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(
            operationId = "registrierenOptionenV1",
            summary = "WebAuthn-Optionen für eine neue Registrierung anfordern",
            description = "Prüft Benutzername und Einwilligung, reserviert eine kurzlebige Registrierung (10 Minuten) und liefert WebAuthn Creation-Options."
    )
    @APIResponse(
            responseCode = "200",
            description = "WebAuthn-Registrierungsoptionen erstellt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WebAuthnOptionenResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Eingabe oder fehlende Einwilligung",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Benutzername bereits vergeben",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "429",
            description = "Rate-Limit überschritten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public WebAuthnOptionenResponse registrierenOptionen(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            RegistrierenOptionenRequest request
    ) {
        String json = passkeyService.starteRegistrierung(
                request.benutzername(),
                Boolean.TRUE.equals(request.einwilligungErteilt())
        );
        return parseOptionen(json);
    }

    @POST
    @Path("/registrieren")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(
            operationId = "registrierenV1",
            summary = "Registrierung mit erstem Passkey abschließen",
            description = "Verifiziert die WebAuthn-Registrierungsantwort, legt Benutzerkonto und ersten Passkey an und meldet den Benutzer direkt an."
    )
    @APIResponse(
            responseCode = "201",
            description = "Benutzer erfolgreich registriert und angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BenutzerResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Registrierungsdaten oder abgelaufene Registrierung",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Benutzername oder Passkey bereits vorhanden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "429",
            description = "Rate-Limit überschritten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response registrieren(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            RegistrierenRequest request
    ) {
        Benutzer benutzer = passkeyService.schliesseRegistrierungAb(
                request.benutzername(),
                request.credential().zuApplication(),
                request.bezeichnung()
        );
        return Response.status(Response.Status.CREATED)
                .entity(BenutzerResponse.ausDomain(benutzer))
                .build();
    }

    @POST
    @Path("/anmelden/optionen")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    @RateLimit(value = 20, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(
            operationId = "anmeldenOptionenV1",
            summary = "WebAuthn-Optionen für die Anmeldung anfordern",
            description = "Liefert WebAuthn Request-Options. Ohne Benutzernamen wird Discoverable Login verwendet."
    )
    @APIResponse(
            responseCode = "200",
            description = "WebAuthn-Anmeldeoptionen erstellt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WebAuthnOptionenResponse.class))
    )
    @APIResponse(
            responseCode = "429",
            description = "Rate-Limit überschritten",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public WebAuthnOptionenResponse anmeldenOptionen(AnmeldenOptionenRequest request) {
        String benutzername = request != null ? request.benutzername() : null;
        String json = passkeyService.starteAnmeldung(benutzername);
        return parseOptionen(json);
    }

    @POST
    @Path("/anmelden")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    @Operation(
            operationId = "anmeldenV1",
            summary = "Mit einem Passkey anmelden",
            description = "Verifiziert die WebAuthn-Assertion, aktualisiert Signaturzähler und UTC-Verwendungsdatum und erstellt eine Sitzung."
    )
    @APIResponse(
            responseCode = "200",
            description = "Anmeldung erfolgreich",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BenutzerResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Eingaben",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Passkey-Verifikation fehlgeschlagen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public BenutzerResponse anmelden(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            AnmeldenRequest request
    ) {
        Benutzer benutzer = passkeyService.meldeAn(request.credential().zuApplication());
        return BenutzerResponse.ausDomain(benutzer);
    }

    @POST
    @Path("/abmelden")
    @Authenticated
    @Operation(
            operationId = "abmeldenV1",
            summary = "Aktuelle Sitzung beenden",
            description = "Beendet die aktuelle Anmeldesitzung des angemeldeten Benutzers."
    )
    @APIResponse(responseCode = "204", description = "Erfolgreich abgemeldet")
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response abmelden() {
        passkeyService.abmelden();
        return Response.noContent().build();
    }

    @GET
    @Path("/ich")
    @Authenticated
    @Operation(
            operationId = "ichV1",
            summary = "Aktuell angemeldeten Benutzer abrufen",
            description = "Liefert die Profildaten des aktuell angemeldeten Benutzers."
    )
    @APIResponse(
            responseCode = "200",
            description = "Benutzerdaten geladen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BenutzerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public BenutzerResponse ich() {
        return BenutzerResponse.ausDomain(passkeyService.aktuellerBenutzer());
    }

    private WebAuthnOptionenResponse parseOptionen(String json) {
        try {
            return new WebAuthnOptionenResponse(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WebAuthn-Optionen konnten nicht serialisiert werden.", e);
        }
    }
}
