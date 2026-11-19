package de.trainingsapp.api.v1.fehler;

import de.trainingsapp.domain.DomainException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Zentrales Exception-Mapping für das einheitliche Fehlerformat FehlerV1.
 */
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @ServerExceptionMapper(priority = Priorities.USER - 100)
    public RestResponse<FehlerResponse> mapConstraintViolation(ConstraintViolationException exception) {
        String meldung = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(nachricht -> nachricht != null && !nachricht.isBlank())
                .sorted()
                .collect(Collectors.joining("; "));
        if (meldung.isBlank()) {
            meldung = "Die Anfrage enthält ungültige Eingaben.";
        }
        return jsonFehler(Response.Status.BAD_REQUEST, "VALIDIERUNG_FEHLGESCHLAGEN", meldung);
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapDomainException(DomainException exception) {
        return jsonFehler(Response.Status.BAD_REQUEST, exception.code(), exception.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapIllegalArgument(IllegalArgumentException exception) {
        String meldung = exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "Die Anfrage enthält ungültige Eingaben.";
        return jsonFehler(Response.Status.BAD_REQUEST, "VALIDIERUNG_FEHLGESCHLAGEN", meldung);
    }

    @ServerExceptionMapper
    public Response mapWebApplicationException(WebApplicationException exception) {
        return exception.getResponse();
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapUnexpectedException(Exception exception) {
        LOG.errorf("Unerwarteter Serverfehler (%s)", exception.getClass().getSimpleName());
        return jsonFehler(
                Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNER_FEHLER",
                "Ein interner Fehler ist aufgetreten."
        );
    }

    private RestResponse<FehlerResponse> jsonFehler(Response.Status status, String code, String meldung) {
        return RestResponse.ResponseBuilder
                .create(status, new FehlerResponse(code, meldung))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
