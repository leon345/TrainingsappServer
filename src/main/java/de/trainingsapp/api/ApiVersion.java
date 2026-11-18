package de.trainingsapp.api;

/**
 * Versionspraefixe fuer alle Endpunkte. Ressourcen setzen ihren Pfad immer mit
 * einem dieser Praefixe, z. B. {@code @Path(ApiVersion.V1 + "/info")}.
 */
public final class ApiVersion {

    public static final String V1 = "/v1";

    private ApiVersion() {
    }
}
