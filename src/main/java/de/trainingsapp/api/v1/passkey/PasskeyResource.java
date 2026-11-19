package de.trainingsapp.api.v1.passkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.api.v1.auth.WebAuthnOptionenResponse;
import de.trainingsapp.api.v1.fehler.FehlerResponse;
import de.trainingsapp.application.passkey.PasskeyService;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST-Endpunkte zur Multi-Passkey-Verwaltung unter /v1/passkeys.
 */
@Path(ApiVersion.V1 + "/passkeys")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Passkeys", description = "Verwaltung der eigenen Passkeys")
public class PasskeyResource {

    private final PasskeyService passkeyService;
    private final ObjectMapper objectMapper;

    public PasskeyResource(PasskeyService passkeyService, ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.objectMapper = objectMapper;
    }

    @GET
    @Operation(
            operationId = "listePasskeysV1",
            summary = "Eigene Passkeys auflisten",
            description = "Liefert alle Passkeys des aktuell angemeldeten Benutzers."
    )
    @APIResponse(
            responseCode = "200",
            description = "Passkeys erfolgreich geladen",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PasskeyResponse[].class)
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public List<PasskeyResponse> auflisten() {
        return passkeyService.listePasskeys().stream()
                .map(PasskeyResponse::ausDomain)
                .toList();
    }

    @POST
    @Path("/optionen")
    @Operation(
            operationId = "passkeyOptionenV1",
            summary = "WebAuthn-Optionen für einen weiteren Passkey anfordern",
            description = "Erzeugt WebAuthn Creation-Options mit Ausschluss bereits registrierter Credentials des angemeldeten Benutzers."
    )
    @APIResponse(
            responseCode = "200",
            description = "WebAuthn-Optionen erstellt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WebAuthnOptionenResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public WebAuthnOptionenResponse optionen() {
        String json = passkeyService.starteWeiterenPasskey();
        try {
            return new WebAuthnOptionenResponse(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WebAuthn-Optionen konnten nicht serialisiert werden.", e);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "passkeyHinzufuegenV1",
            summary = "Weiteren Passkey hinzufügen",
            description = "Verifiziert die WebAuthn-Antwort und speichert einen weiteren Passkey mit automatischer Bezeichnung 'Passkey n' (oder Wunschbezeichnung)."
    )
    @APIResponse(
            responseCode = "201",
            description = "Passkey erfolgreich hinzugefügt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PasskeyResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige WebAuthn-Antwort oder Bezeichnung",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Passkey bereits registriert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response hinzufuegen(
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            PasskeyHinzufuegenRequest request
    ) {
        Passkey erstellt = passkeyService.fuegePasskeyHinzu(
                request.credential().zuApplication(),
                request.bezeichnung()
        );
        return Response.status(Response.Status.CREATED)
                .entity(PasskeyResponse.ausDomain(erstellt))
                .build();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("passkey:verwalten")
    @Operation(
            operationId = "passkeyUmbenennenV1",
            summary = "Passkey umbenennen",
            description = "Ändert die Bezeichnung eines eigenen Passkeys."
    )
    @APIResponse(
            responseCode = "200",
            description = "Passkey erfolgreich umbenannt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PasskeyResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Ungültige Bezeichnung",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Passkey nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public PasskeyResponse umbenennen(
            @PathParam("id") UUID id,
            @NotNull(message = "Anfragekörper darf nicht fehlen")
            @Valid
            PasskeyUmbenennenRequest request
    ) {
        Passkey aktualisiert = passkeyService.benennePasskeyUm(new PasskeyId(id), request.bezeichnung());
        return PasskeyResponse.ausDomain(aktualisiert);
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("passkey:verwalten")
    @Operation(
            operationId = "passkeyLoeschenV1",
            summary = "Passkey löschen",
            description = "Löscht einen eigenen Passkey. Der letzte verbleibende Passkey eines Kontos darf nicht gelöscht werden."
    )
    @APIResponse(responseCode = "204", description = "Passkey erfolgreich gelöscht")
    @APIResponse(
            responseCode = "401",
            description = "Nicht angemeldet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Passkey nicht gefunden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    @APIResponse(
            responseCode = "409",
            description = "Letzter Passkey darf nicht gelöscht werden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FehlerResponse.class))
    )
    public Response loeschen(@PathParam("id") UUID id) {
        passkeyService.loeschePasskey(new PasskeyId(id));
        return Response.noContent().build();
    }
}
