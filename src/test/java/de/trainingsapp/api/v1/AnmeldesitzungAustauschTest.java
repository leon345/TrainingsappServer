package de.trainingsapp.api.v1;

import static de.trainingsapp.testhilfe.Ablauf.anmeldeOptionen;
import static de.trainingsapp.testhilfe.Ablauf.anmeldungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.im;
import static de.trainingsapp.testhilfe.Ablauf.registrierungAbschliessen;
import static de.trainingsapp.testhilfe.Ablauf.registrierungsOptionen;
import static de.trainingsapp.testhilfe.sitzung.HeaderSitzungen.HEADER;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.trainingsapp.infrastructure.persistenz.DatenbankErforderlich;
import de.trainingsapp.testhilfe.TestAuthenticator;
import de.trainingsapp.testhilfe.sitzung.HeaderSitzungProfil;
import de.trainingsapp.testhilfe.sitzung.HeaderSitzungen;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * E23, Abnahme 6 (Austauschtest): Mit einer zweiten Umsetzung des Ports Anmeldesitzung (Token im
 * Header, nur in den Testquellen) laufen Registrierung, Anmeldung und die geschuetzten Endpunkte
 * unveraendert; Ablauf und Widerruf der Sitzung sind getestet. Die WebAuthn-Challenge liegt weiter
 * im Cookie der Extension (bekannte Grenze, docs/auth-erweiterung.md).
 */
@QuarkusTest
@TestProfile(HeaderSitzungProfil.class)
@DatenbankErforderlich
class AnmeldesitzungAustauschTest {

    @TestHTTPResource
    URL url;

    @Inject
    HeaderSitzungen sitzungen;

    @AfterEach
    void uhrZuruecksetzen() {
        sitzungen.verstelleUhr(Duration.ZERO);
    }

    @Test
    void geschuetzteEndpunkteLaufenMitDerHeaderSitzungUnveraendert() {
        TestAuthenticator handy = new TestAuthenticator(url);
        TestAuthenticator laptop = new TestAuthenticator(url);

        // Registrierung: Token im Header, kein Sitzungs-Cookie
        CookieFilter zeremonie = new CookieFilter();
        Response registriert = registrierungAbschliessen(zeremonie, "header-anna",
                handy.registriere(registrierungsOptionen(zeremonie, "header-anna")));
        registriert.then().statusCode(204)
                .header("Set-Cookie", not(containsString("quarkus-credential=")));
        String token = registriert.getHeader(HEADER);
        assertNotNull(token);

        // Geschuetzte Endpunkte mit Token
        mit(token).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(1));
        // Die WebAuthn-Challenge liegt weiter im Cookie der Extension (bekannte Grenze)
        JsonObject optionen = new JsonObject(im(zeremonie).header(HEADER, token).post("/v1/passkeys/optionen")
                .then().statusCode(200).extract().asString());
        String laptopId = im(zeremonie).header(HEADER, token).contentType(ContentType.JSON).body(laptop.registriere(optionen).encode())
                .post("/v1/passkeys").then().statusCode(201).extract().path("id");
        mit(token).contentType(ContentType.JSON).body("{\"bezeichnung\":\"Laptop\"}")
                .patch("/v1/passkeys/" + laptopId).then().statusCode(200).body("bezeichnung", equalTo("Laptop"));
        mit(token).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(2));

        // Ohne Token: 401 (kein Cookie-Fallback)
        im(zeremonie).get("/v1/passkeys").then().statusCode(401);

        // Anmeldung mit dem zweiten Passkey liefert ein neues Token desselben Benutzers
        CookieFilter zweiterBrowser = new CookieFilter();
        Response angemeldet = anmeldungAbschliessen(zweiterBrowser, laptop.meldeAn(anmeldeOptionen(zweiterBrowser)));
        angemeldet.then().statusCode(204);
        String token2 = angemeldet.getHeader(HEADER);
        assertNotNull(token2);
        assertNotEquals(token, token2);
        mit(token2).delete("/v1/passkeys/" + laptopId).then().statusCode(204);
        mit(token).get("/v1/passkeys").then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void ablaufDerSitzung() {
        String token = registrieren("header-ablauf");
        mit(token).get("/v1/passkeys").then().statusCode(200);

        sitzungen.verstelleUhr(Duration.ofMinutes(14));
        mit(token).get("/v1/passkeys").then().statusCode(200);

        sitzungen.verstelleUhr(Duration.ofMinutes(16));
        mit(token).get("/v1/passkeys").then().statusCode(401);
    }

    @Test
    void widerrufDerSitzungBeimAbmelden() {
        String token = registrieren("header-widerruf");
        mit(token).post("/v1/abmelden").then().statusCode(204);

        mit(token).get("/v1/passkeys").then().statusCode(401);
        mit(token).post("/v1/abmelden").then().statusCode(401);
    }

    private String registrieren(String name) {
        CookieFilter zeremonie = new CookieFilter();
        Response antwort = registrierungAbschliessen(zeremonie, name,
                new TestAuthenticator(url).registriere(registrierungsOptionen(zeremonie, name)));
        antwort.then().statusCode(204);
        return antwort.getHeader(HEADER);
    }

    private static RequestSpecification mit(String token) {
        return im(new CookieFilter()).header(HEADER, token);
    }
}
