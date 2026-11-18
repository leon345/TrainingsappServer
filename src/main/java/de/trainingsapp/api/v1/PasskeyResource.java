package de.trainingsapp.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.application.passkey.PasskeyService;
import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.passkey.PasskeyId;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Passkey-Verwaltung des angemeldeten Benutzers. Der Benutzer ergibt sich
 * ausschliesslich aus der Sitzung, nie aus Pfad oder Body.
 */
@Path(ApiVersion.V1 + "/passkeys")
@Authenticated
@Tag(name = "Passkeys")
@Produces(MediaType.APPLICATION_JSON)
public class PasskeyResource {

    private final PasskeyService service;
    private final SecurityIdentity identity;

    public PasskeyResource(PasskeyService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @GET
    @Operation(operationId = "listPasskeysV1", summary = "Eigene Passkeys auflisten")
    @APIResponse(responseCode = "200", description = "Passkeys, aelteste zuerst")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet")
    public List<PasskeyResponse> liste() {
        return service.liste(angemeldet()).stream().map(PasskeyResponse::from).toList();
    }

    @POST
    @Path("/optionen")
    @Operation(operationId = "startPasskeyV1", summary = "Weiteren Passkey hinzufuegen: starten",
            description = "Registrierungsoptionen fuer den angemeldeten Benutzer (gleicher User-Handle, "
                    + "vorhandene Passkeys in excludeCredentials).")
    @APIResponse(responseCode = "200", description = "Optionen fuer navigator.credentials.create()")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet")
    public String optionen() {
        return service.weiterenPasskeyStarten(angemeldet());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createPasskeyV1", summary = "Weiteren Passkey hinzufuegen: abschliessen")
    @APIResponse(responseCode = "201", description = "Passkey hinzugefuegt")
    @APIResponse(responseCode = "400", description = "Passkey ungueltig oder Challenge fehlt")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet")
    public Response hinzufuegen(JsonNode credential) {
        if (credential == null) {
            throw new BadRequestException("credential fehlt");
        }
        PasskeyResponse neu = PasskeyResponse.from(
                service.weiterenPasskeyAbschliessen(angemeldet(), credential.toString()));
        return Response.status(Response.Status.CREATED).entity(neu).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "deletePasskeyV1", summary = "Passkey loeschen")
    @APIResponse(responseCode = "204", description = "Geloescht")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet")
    @APIResponse(responseCode = "404", description = "Kein eigener Passkey mit dieser ID")
    @APIResponse(responseCode = "409", description = "Letzter Passkey, Loeschen gesperrt")
    public void loeschen(@PathParam("id") String id) {
        service.loesche(angemeldet(), new PasskeyId(id));
    }

    private BenutzerId angemeldet() {
        return new BenutzerId(identity.getPrincipal().getName());
    }
}
