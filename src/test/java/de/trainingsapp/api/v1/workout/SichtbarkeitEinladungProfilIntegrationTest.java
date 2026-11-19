package de.trainingsapp.api.v1.workout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SichtbarkeitEinladungProfilIntegrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void vollstaendigeAbacMatrixFuerSichtbarkeitEinladungenSucheUndProfile() throws Exception {
        var besitzer = TestBenutzerHelfer.registriereBenutzer("abac.own");
        var eingeladener = TestBenutzerHelfer.registriereBenutzer("abac.inv");
        var fremderUser = TestBenutzerHelfer.registriereBenutzer("abac.usr");
        var admin = TestBenutzerHelfer.registriereBenutzer("abac.adm");
        setzeRolleAdmin(admin.id().wert());

        // 1. Exakte Benutzernamenssuche (/v1/benutzer/suche)
        given()
                .when().get("/v1/benutzer/suche?name=" + eingeladener.benutzername())
                .then()
                .statusCode(401)
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/benutzer/suche?name=" + eingeladener.benutzername().toUpperCase())
                .then()
                .statusCode(200)
                .body("id", equalTo(eingeladener.id().alsText()))
                .body("benutzername", equalTo(eingeladener.benutzername()));

        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/benutzer/suche?name=unbekannt.99999999")
                .then()
                .statusCode(404)
                .body("code", equalTo("BENUTZER_NICHT_GEFUNDEN"));

        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/benutzer/suche?name=a")
                .then()
                .statusCode(400);

        // 2. Besitzer legt privates und öffentliches Workout an
        String privatesWorkoutId = erstelleWorkout(besitzer.sessionCookie(), "Privates Krafttraining");
        String oeffentlichesWorkoutId = erstelleWorkout(besitzer.sessionCookie(), "Öffentliches Calisthenics");

        // Sichtbarkeit umschalten: nur Besitzer erlaubt
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", fremderUser.sessionCookie())
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", admin.sessionCookie())
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(200)
                .body("sichtbarkeit", equalTo("OEFFENTLICH"))
                .body("version", equalTo(1));

        // 3. Einladungen verwalten (Selbsteinladung 400, unbekannt 404, Erstellung 201, Duplikat 409)
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .body(Map.of("benutzername", besitzer.benutzername()))
                .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(400)
                .body("code", equalTo("SELBSTEINLADUNG_NICHT_ERLAUBT"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .body(Map.of("benutzername", "nicht.vorhanden.1234"))
                .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(404)
                .body("code", equalTo("BENUTZER_NICHT_GEFUNDEN"));

        Response einladungResp = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .body(Map.of("benutzername", eingeladener.benutzername()))
                .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(201)
                .body("workoutId", equalTo(privatesWorkoutId))
                .body("eingeladenerId", equalTo(eingeladener.id().alsText()))
                .body("eingeladenerBenutzername", equalTo(eingeladener.benutzername()))
                .body("status", equalTo("AKTIV"))
                .body("erstelltAm", notNullValue())
                .extract().response();
        einladungResp.then().header(
                "Location",
                endsWith("/v1/workouts/" + privatesWorkoutId + "/einladungen/" + eingeladener.id().alsText())
        );

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .body(Map.of("benutzername", eingeladener.benutzername()))
                .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(409)
                .body("code", equalTo("EINLADUNG_EXISTIERT_BEREITS"));

        // Einladungen auflisten: nur Besitzer
        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].eingeladenerId", equalTo(eingeladener.id().alsText()));

        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(404);

        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(404);

        // 4. Geteilte Workouts (/v1/workouts/geteilt)
        given()
                .when().get("/v1/workouts/geteilt")
                .then()
                .statusCode(401);

        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/geteilt")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(privatesWorkoutId))
                .body("[0].ownerId", equalTo(besitzer.id().alsText()))
                .body("[0].ownerBenutzername", equalTo(besitzer.benutzername()))
                .body("[0].eingeladenAm", notNullValue());

        given()
                .cookie("quarkus-credential", fremderUser.sessionCookie())
                .when().get("/v1/workouts/geteilt")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        // 5. ABAC-Matrix auf GET /v1/workouts/{id} (PRIVAT)
        given().cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId).then().statusCode(200);
        given().cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId).then().statusCode(200);
        given().cookie("quarkus-credential", fremderUser.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId).then().statusCode(404);
        given().cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId).then().statusCode(404);
        given()
                .when().get("/v1/workouts/" + privatesWorkoutId).then().statusCode(401);

        // 6. ABAC-Matrix auf GET /v1/workouts/{id} (OEFFENTLICH)
        given().cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId).then().statusCode(200);
        given().cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId).then().statusCode(200);
        given().cookie("quarkus-credential", fremderUser.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId).then().statusCode(200);
        // ADMIN ohne Besitz/Einladung darf KEINE fremden Trainingsdaten lesen (auch nicht öffentlich!)
        given().cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId).then().statusCode(404);
        given()
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId).then().statusCode(401);

        // 7. Schreibzugriffe (PUT/DELETE/PATCH) bleiben 100 % besitzer-exklusiv (auch für Eingeladenen!)
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + privatesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(404);
        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().delete("/v1/workouts/" + privatesWorkoutId)
                .then()
                .statusCode(404);

        // 8. ABAC-Matrix auf GET /v1/profile/{benutzerId}/workouts
        given()
                .when().get("/v1/profile/" + besitzer.id().alsText() + "/workouts")
                .then()
                .statusCode(401)
                .body("code", equalTo("NICHT_ANGEMELDET"));

        // Besitzer sieht eigene öffentliche Workouts (nicht private)
        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().get("/v1/profile/" + besitzer.id().alsText() + "/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(oeffentlichesWorkoutId))
                .body("[0].sichtbarkeit", equalTo("OEFFENTLICH"));

        // Anderer USER und Eingeladener sehen ausschließlich öffentliche Workouts
        given()
                .cookie("quarkus-credential", fremderUser.sessionCookie())
                .when().get("/v1/profile/" + besitzer.id().alsText() + "/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(oeffentlichesWorkoutId));

        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/profile/" + besitzer.id().alsText() + "/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(oeffentlichesWorkoutId));

        // Fremder ADMIN erhält keinen Zugriff auf fremdes Profil (403)
        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/profile/" + besitzer.id().alsText() + "/workouts")
                .then()
                .statusCode(403);

        // ADMIN auf eigenes Profil erlaubt (Besitzer darf eigene Daten lesen)
        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/profile/" + admin.id().alsText() + "/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        // Unbekannte Benutzer-ID -> 404
        given()
                .cookie("quarkus-credential", fremderUser.sessionCookie())
                .when().get("/v1/profile/" + UUID.randomUUID() + "/workouts")
                .then()
                .statusCode(404)
                .body("code", equalTo("BENUTZER_NICHT_GEFUNDEN"));

        // 9. Einladung entfernen -> Zugriff erlischt sofort
        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().delete("/v1/workouts/" + privatesWorkoutId + "/einladungen/" + eingeladener.id().alsText())
                .then()
                .statusCode(404);

        given()
                .cookie("quarkus-credential", besitzer.sessionCookie())
                .when().delete("/v1/workouts/" + privatesWorkoutId + "/einladungen/" + eingeladener.id().alsText())
                .then()
                .statusCode(204);

        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId)
                .then()
                .statusCode(404);

        given()
                .cookie("quarkus-credential", eingeladener.sessionCookie())
                .when().get("/v1/workouts/geteilt")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    private String erstelleWorkout(String sessionCookie, String titel) {
        Map<String, Object> payload = Map.of(
                "typ", "CALISTHENICS",
                "titel", titel,
                "startZeit", "2026-11-19T12:00:00Z",
                "dauerSekunden", 1800,
                "uebungen", List.of(
                        Map.of(
                                "name", "Liegestütze",
                                "saetze", List.of(Map.of("wiederholungen", 15, "pauseSekunden", 60))
                        )
                )
        );
        return given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", sessionCookie)
                .body(payload)
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private void setzeRolleAdmin(UUID benutzerId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement("UPDATE benutzer SET rolle = 'ADMIN' WHERE id = ?")) {
            stmt.setObject(1, benutzerId);
            stmt.executeUpdate();
        }
    }
}
