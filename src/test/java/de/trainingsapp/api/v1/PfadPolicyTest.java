package de.trainingsapp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import de.trainingsapp.testhilfe.TestOrigin;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * E25/E16: Alle Pfade unter /v1/* ausser anmelden* und registrieren* liefern ohne Anmeldung
 * 401 als JSON, nie 302 - auch nicht vorhandene Pfade, damit nichts ueber Routen verraten wird.
 */
@QuarkusTest
class PfadPolicyTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /v1/info",
            "GET, /v1/passkeys",
            "POST, /v1/passkeys/optionen",
            "DELETE, /v1/passkeys/abc",
            "PATCH, /v1/passkeys/abc",
            "POST, /v1/abmelden",
            "GET, /v1",
            "GET, /v1/",
            "GET, /v1/gibt-es-nicht",
            "POST, /v1/gibt/es/auch/nicht",
            "GET, /v1/timer/einstellung",
            "GET, /v1/anmeldenx",
            "GET, /v1/registrierenx"})
    void ohneAnmeldung401AlsJson(String methode, String pfad) {
        anfrage(methode, pfad)
                .statusCode(401)
                .contentType("application/json")
                .header("Location", nullValue())
                .body("code", equalTo("NICHT_ANGEMELDET"))
                .body("meldung", equalTo("Anmeldung erforderlich"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST, /v1/anmelden/optionen",
            "GET, /v1/anmelden/gibt-es-nicht",
            "GET, /v1/registrieren/gibt-es-nicht"})
    void anmeldenUndRegistrierenSindOhneAnmeldungErreichbar(String methode, String pfad) {
        anfrage(methode, pfad).statusCode(not(401)).header("Location", nullValue());
    }

    private static ValidatableResponse anfrage(String methode, String pfad) {
        return given().redirects().follow(false).header("Origin", TestOrigin.wert())
                .request(methode, pfad).then();
    }
}
