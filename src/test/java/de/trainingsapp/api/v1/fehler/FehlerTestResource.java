package de.trainingsapp.api.v1.fehler;

import de.trainingsapp.api.ApiVersion;
import de.trainingsapp.domain.DomainException;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Hilfs-Ressource ausschließlich für Tests des Fehler-Mappings (FehlerV1).
 */
@Path(ApiVersion.V1 + "/test-fehler")
@Produces(MediaType.APPLICATION_JSON)
public class FehlerTestResource {

    @GET
    @Path("/validierung")
    public String validierung(@QueryParam("dauerSekunden") @Min(value = 1, message = "dauerSekunden muss mindestens 1 sein") int dauerSekunden) {
        return String.valueOf(dauerSekunden);
    }

    @GET
    @Path("/domain")
    public String domainFehler() {
        throw new DomainException("FACHLICHER_FEHLER", "Fachliche Regel verletzt.");
    }
}
