package de.trainingsapp.api.v1.fehler;

import de.trainingsapp.application.auth.Anmeldesitzung;
import de.trainingsapp.domain.DomainException;
import de.trainingsapp.domain.DomainKonfliktException;
import de.trainingsapp.domain.DomainNichtAutorisiertException;
import de.trainingsapp.domain.DomainNichtGefundenException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.stream.Collectors;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Zentrales Exception-Mapping für das einheitliche Fehlerformat FehlerV1.
 */
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    private final Anmeldesitzung anmeldesitzung;
    private final ManagedExecutor managedExecutor;

    public ApiExceptionMapper(Anmeldesitzung anmeldesitzung, ManagedExecutor managedExecutor) {
        this.anmeldesitzung = anmeldesitzung;
        this.managedExecutor = managedExecutor;
    }

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

    @ServerExceptionMapper(priority = Priorities.USER - 100)
    public RestResponse<FehlerResponse> mapUnauthorized(UnauthorizedException exception) {
        return jsonFehler(
                Response.Status.UNAUTHORIZED,
                "NICHT_ANGEMELDET",
                "Für diese Anfrage ist eine Anmeldung erforderlich."
        );
    }

    @ServerExceptionMapper(priority = Priorities.USER - 100)
    public RestResponse<FehlerResponse> mapAuthenticationFailed(AuthenticationFailedException exception) {
        return jsonFehler(
                Response.Status.UNAUTHORIZED,
                "ANMELDUNG_FEHLGESCHLAGEN",
                "Die Anmeldung ist fehlgeschlagen."
        );
    }

    @ServerExceptionMapper(priority = Priorities.USER - 100)
    public RestResponse<FehlerResponse> mapForbidden(ForbiddenException exception) {
        if (exception.getCause() instanceof DomainNichtGefundenException nichtGefunden) {
            return mapDomainNichtGefunden(nichtGefunden);
        }
        return jsonFehler(
                Response.Status.FORBIDDEN,
                "ZUGRIFF_VERWEIGERT",
                "Du hast keine Berechtigung für diese Aktion."
        );
    }

    @ServerExceptionMapper(priority = Priorities.USER - 100)
    public RestResponse<FehlerResponse> mapRateLimit(RateLimitException exception) {
        return jsonFehler(
                Response.Status.TOO_MANY_REQUESTS,
                "ZU_VIELE_ANFRAGEN",
                "Zu viele Anfragen. Bitte warte einen Moment und versuche es erneut."
        );
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapDomainNichtAutorisiert(DomainNichtAutorisiertException exception) {
        return jsonFehler(Response.Status.UNAUTHORIZED, exception.code(), exception.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapDomainNichtGefunden(DomainNichtGefundenException exception) {
        return jsonFehler(Response.Status.NOT_FOUND, exception.code(), exception.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<FehlerResponse> mapDomainKonflikt(DomainKonfliktException exception) {
        return jsonFehler(Response.Status.CONFLICT, exception.code(), exception.getMessage());
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
    public Uni<Response> mapNotFound(NotFoundException exception, UriInfo uriInfo) {
        String pfad = uriInfo != null && uriInfo.getPath() != null ? uriInfo.getPath() : "";
        boolean istApiV1 = pfad.equals("v1") || pfad.startsWith("v1/")
                || pfad.equals("/v1") || pfad.startsWith("/v1/");
        if (!istApiV1) {
            return Uni.createFrom().item(exception.getResponse());
        }
        return Uni.createFrom().item(() -> {
            if (anmeldesitzung.aktuellerBenutzer().isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .entity(new FehlerResponse(
                                "NICHT_ANGEMELDET",
                                "Für diese Anfrage ist eine Anmeldung erforderlich."
                        ))
                        .build();
            }
            return exception.getResponse();
        }).runSubscriptionOn(managedExecutor);
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
