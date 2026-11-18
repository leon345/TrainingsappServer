package de.trainingsapp.testhilfe;

import static io.restassured.RestAssured.given;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

/**
 * Testhilfe fuer die Passkey-Zeremonien ueber die /v1-Endpunkte. Ein {@link CookieFilter} steht fuer
 * einen Browser; schreibende Requests tragen den Origin des Servers, wie ein Browser ihn sendet.
 */
public final class Ablauf {

    private Ablauf() {
    }

    /** Request mit Cookies des Browsers und dem Origin des Testservers. */
    public static RequestSpecification im(CookieFilter browser) {
        return given().filter(browser).header("Origin", TestOrigin.wert());
    }

    public static JsonObject registrierungsOptionen(CookieFilter browser, String name) {
        return new JsonObject(im(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name).encode())
                .post("/v1/registrieren/optionen").then().statusCode(200).extract().asString());
    }

    public static Response registrierungAbschliessen(CookieFilter browser, String name, JsonObject credential) {
        return im(browser).contentType(ContentType.JSON)
                .body(new JsonObject().put("benutzername", name).put("credential", credential).encode())
                .post("/v1/registrieren");
    }

    /** Registriert einen neuen Benutzer mit {@code geraet}; der Browser ist danach angemeldet. */
    public static CookieFilter registrieren(String name, TestAuthenticator geraet) {
        CookieFilter browser = new CookieFilter();
        JsonObject optionen = registrierungsOptionen(browser, name);
        registrierungAbschliessen(browser, name, geraet.registriere(optionen)).then().statusCode(204);
        return browser;
    }

    public static JsonObject anmeldeOptionen(CookieFilter browser) {
        return new JsonObject(im(browser).post("/v1/anmelden/optionen")
                .then().statusCode(200).extract().asString());
    }

    public static Response anmeldungAbschliessen(CookieFilter browser, JsonObject credential) {
        return im(browser).contentType(ContentType.JSON).body(credential.encode()).post("/v1/anmelden");
    }

    /** Meldet sich in einem frischen Browser mit {@code geraet} an (ohne Benutzernamen). */
    public static CookieFilter anmelden(TestAuthenticator geraet) {
        CookieFilter browser = new CookieFilter();
        anmeldungAbschliessen(browser, geraet.meldeAn(anmeldeOptionen(browser))).then().statusCode(204);
        return browser;
    }

    public static JsonObject hinzufuegenOptionen(CookieFilter browser) {
        return new JsonObject(im(browser).post("/v1/passkeys/optionen")
                .then().statusCode(200).extract().asString());
    }

    /** Fuegt dem angemeldeten Benutzer einen weiteren Passkey hinzu und liefert dessen ID. */
    public static String hinzufuegen(CookieFilter browser, TestAuthenticator geraet) {
        return im(browser).contentType(ContentType.JSON)
                .body(geraet.registriere(hinzufuegenOptionen(browser)).encode())
                .post("/v1/passkeys").then().statusCode(201).extract().path("id");
    }
}
