package de.trainingsapp.api.v1.timer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TimerResourceIntegrationTest {

    @Test
    void standardWertSpeichernAktualisierenValidierungUndBenutzertrennung() {
        var nutzerA = TestBenutzerHelfer.registriereBenutzer("timer.a");
        var nutzerB = TestBenutzerHelfer.registriereBenutzer("timer.b");

        // 1. GET ohne gespeicherten Eintrag -> Standard 30 / 10, gespeichert=false (nicht persistiert)
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(30))
                .body("pauseSekunden", equalTo(10))
                .body("gespeichert", equalTo(false));

        // Zweites GET liefert weiterhin gespeichert=false
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("gespeichert", equalTo(false));

        // 2. PUT speichert Einstellung und ignoriert readOnly/unbekannte Felder wie gespeichert
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 45, "pauseSekunden", 15, "gespeichert", false))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(45))
                .body("pauseSekunden", equalTo(15))
                .body("gespeichert", equalTo(true));

        // 3. GET liefert gespeicherte Werte mit gespeichert=true
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(45))
                .body("pauseSekunden", equalTo(15))
                .body("gespeichert", equalTo(true));

        // 4. Erneutes PUT aktualisiert denselben Eintrag (inkl. Grenzwerte 1 / 0 und 86400 / 86400)
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 60, "pauseSekunden", 0))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(60))
                .body("pauseSekunden", equalTo(0))
                .body("gespeichert", equalTo(true));

        // 5. Trennung zwischen Benutzern: Nutzer B sieht weiterhin Standard (gespeichert=false)
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(30))
                .body("pauseSekunden", equalTo(10))
                .body("gespeichert", equalTo(false));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(Map.of("trainingSekunden", 120, "pauseSekunden", 30))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(120))
                .body("pauseSekunden", equalTo(30))
                .body("gespeichert", equalTo(true));

        // Nutzer A behält seine eigenen Werte
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(60))
                .body("pauseSekunden", equalTo(0))
                .body("gespeichert", equalTo(true));

        // 6. Validierungsfehler -> 400 VALIDIERUNG_FEHLGESCHLAGEN
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 0, "pauseSekunden", 10))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 86401, "pauseSekunden", 10))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 30, "pauseSekunden", -1))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 30))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"));
    }

    @Test
    void unauthentifizierteTimerAnfragenLiefern401FehlerV1JsonOhneRedirect() {
        given()
                .redirects().follow(false)
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("Location", nullValue())
                .body("code", equalTo("NICHT_ANGEMELDET"))
                .body("meldung", notNullValue());

        given()
                .redirects().follow(false)
                .contentType(ContentType.JSON)
                .body(Map.of("trainingSekunden", 40, "pauseSekunden", 20))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("Location", nullValue())
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }
}
