package de.trainingsapp.infrastructure.passkey;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Blockiert die von quarkus-security-webauthn standardmäßig registrierten /q/webauthn/*-Routen
 * (login-options-challenge, register-options-challenge, webauthn.js, logout) mit 404 ohne Body/Cookie.
 * Freigegebene Option A gemäß Nutzerentscheid.
 */
@ApplicationScoped
public class WebAuthnStandardRoutenBlocker {

    void blockiereStandardRouten(@Observes Router router) {
        router.route("/q/webauthn").order(-100).handler(ctx ->
                ctx.response().setStatusCode(404).end()
        );
        router.route("/q/webauthn/*").order(-100).handler(ctx ->
                ctx.response().setStatusCode(404).end()
        );
    }
}
