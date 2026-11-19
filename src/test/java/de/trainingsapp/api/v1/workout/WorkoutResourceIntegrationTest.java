package de.trainingsapp.api.v1.workout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WorkoutResourceIntegrationTest {

    @Inject
    RollenTestHelfer rollenHelfer;

    @Test
    void kraftUndCalisthenicsCrudFilterUndOptimistischeSperre() {
        var nutzer = TestBenutzerHelfer.registriereBenutzer("wk.crud");

        // 1. Kraft-Workout erstellen -> 201 Created + Location + version=0 + PRIVAT + MANUELL
        Map<String, Object> kraftPayload = Map.of(
                "typ", "KRAFT",
                "titel", "Ganzkörper Kraft",
                "startZeit", "2026-11-18T17:00:00Z",
                "dauerSekunden", 3600,
                "notiz", "Fokus auf saubere Technik",
                "uebungen", List.of(
                        Map.of(
                                "name", "Kniebeuge",
                                "saetze", List.of(
                                        Map.of("wiederholungen", 5, "gewichtKg", 100.5, "pauseSekunden", 180),
                                        Map.of("wiederholungen", 5, "gewichtKg", 105.0, "pauseSekunden", 180)
                                )
                        )
                )
        );

        Response kraftResp = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .body(kraftPayload)
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("typ", equalTo("KRAFT"))
                .body("titel", equalTo("Ganzkörper Kraft"))
                .body("dauerSekunden", equalTo(3600))
                .body("sichtbarkeit", equalTo("PRIVAT"))
                .body("herkunft", equalTo("MANUELL"))
                .body("version", equalTo(0))
                .body("uebungen", hasSize(1))
                .body("uebungen[0].name", equalTo("Kniebeuge"))
                .body("uebungen[0].position", equalTo(0))
                .body("uebungen[0].saetze", hasSize(2))
                .body("uebungen[0].saetze[0].wiederholungen", equalTo(5))
                .body("uebungen[0].saetze[0].gewichtKg", equalTo(100.5f))
                .body("uebungen[0].saetze[1].position", equalTo(1))
                .extract().response();

        String kraftId = kraftResp.path("id");
        kraftResp.then().header("Location", endsWith("/v1/workouts/" + kraftId));

        // 2. Calisthenics-Workout mit Progressions-Variante, Haltedauer, Zusatzgewicht und Satzpause
        Map<String, Object> caliPayload = Map.of(
                "typ", "CALISTHENICS",
                "titel", "Skills & Pull",
                "startZeit", "2026-11-19T18:30:00Z",
                "dauerSekunden", 2700,
                "uebungen", List.of(
                        Map.of(
                                "name", "Front Lever",
                                "variante", "Tuck",
                                "saetze", List.of(
                                        Map.of("dauerSekunden", 20, "pauseSekunden", 90),
                                        Map.of("dauerSekunden", 18, "pauseSekunden", 90)
                                )
                        ),
                        Map.of(
                                "name", "Klimmzüge",
                                "variante", "Weighted",
                                "saetze", List.of(
                                        Map.of("wiederholungen", 6, "gewichtKg", 12.5, "pauseSekunden", 120)
                                )
                        )
                )
        );

        String caliId = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .body(caliPayload)
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .body("typ", equalTo("CALISTHENICS"))
                .body("uebungen", hasSize(2))
                .body("uebungen[0].name", equalTo("Front Lever"))
                .body("uebungen[0].variante", equalTo("Tuck"))
                .body("uebungen[0].saetze[0].dauerSekunden", equalTo(20))
                .body("uebungen[1].variante", equalTo("Weighted"))
                .body("uebungen[1].saetze[0].gewichtKg", equalTo(12.5f))
                .extract().path("id");

        // 3. Auflisten: absteigend nach startZeit (Calisthenics 19.11. vor Kraft 18.11.)
        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .when().get("/v1/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].id", equalTo(caliId))
                .body("[1].id", equalTo(kraftId));

        // 4. Filtern nach Typ
        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .queryParam("typ", "CALISTHENICS")
                .when().get("/v1/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(caliId));

        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .queryParam("typ", "KRAFT")
                .when().get("/v1/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(kraftId));

        // 5. Detail abrufen
        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .when().get("/v1/workouts/" + caliId)
                .then()
                .statusCode(200)
                .body("id", equalTo(caliId))
                .body("uebungen[0].variante", equalTo("Tuck"))
                .body("version", equalTo(0));

        // 6. Aktualisieren mit korrekter Version 0 -> version=1
        Map<String, Object> updatePayload = Map.of(
                "version", 0,
                "typ", "CALISTHENICS",
                "titel", "Skills & Pull (Progression)",
                "startZeit", "2026-11-19T18:30:00Z",
                "dauerSekunden", 3000,
                "notiz", "Advanced Tuck getestet",
                "uebungen", List.of(
                        Map.of(
                                "name", "Front Lever",
                                "variante", "Advanced Tuck",
                                "saetze", List.of(
                                        Map.of("dauerSekunden", 15, "pauseSekunden", 120)
                                )
                        )
                )
        );

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .body(updatePayload)
                .when().put("/v1/workouts/" + caliId)
                .then()
                .statusCode(200)
                .body("titel", equalTo("Skills & Pull (Progression)"))
                .body("dauerSekunden", equalTo(3000))
                .body("version", equalTo(1))
                .body("uebungen", hasSize(1))
                .body("uebungen[0].variante", equalTo("Advanced Tuck"));

        // 7. Optimistische Sperre: veraltete Version 0 -> 409 WORKOUT_VERSIONSKONFLIKT
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .body(updatePayload)
                .when().put("/v1/workouts/" + caliId)
                .then()
                .statusCode(409)
                .body("code", equalTo("WORKOUT_VERSIONSKONFLIKT"));

        // 8. Validierungsfehler: Satz ohne wiederholungen und ohne dauerSekunden -> 400
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .body(Map.of(
                        "typ", "KRAFT",
                        "titel", "Ungültig",
                        "startZeit", "2026-11-19T18:00:00Z",
                        "uebungen", List.of(Map.of(
                                "name", "Bankdrücken",
                                "saetze", List.of(Map.of("gewichtKg", 50))
                        ))
                ))
                .when().post("/v1/workouts")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDIERUNG_FEHLGESCHLAGEN"));

        // 9. Löschen -> 204, danach 404
        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .when().delete("/v1/workouts/" + caliId)
                .then()
                .statusCode(204);

        given()
                .cookie("quarkus-credential", nutzer.sessionCookie())
                .when().get("/v1/workouts/" + caliId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));
    }

    @Test
    void idorSchutzVerhindertFremdzugriffDurchAnderenUserUndAdminOhneExistenzleck() {
        var nutzerA = TestBenutzerHelfer.registriereBenutzer("wk.owner");
        var nutzerB = TestBenutzerHelfer.registriereBenutzer("wk.other");
        var admin = TestBenutzerHelfer.registriereBenutzer("wk.adm");
        rollenHelfer.setzeRolleAdmin(admin.id().wert());

        String privatesWorkoutId = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of(
                        "typ", "CALISTHENICS",
                        "titel", "Privates Training A",
                        "startZeit", "2026-11-19T18:00:00Z",
                        "sichtbarkeit", "PRIVAT"
                ))
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .extract().path("id");

        String oeffentlichesWorkoutId = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of(
                        "typ", "KRAFT",
                        "titel", "Öffentliches Training A",
                        "startZeit", "2026-11-19T19:00:00Z",
                        "sichtbarkeit", "OEFFENTLICH"
                ))
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .extract().path("id");

        Map<String, Object> fremdUpdate = Map.of(
                "version", 0,
                "typ", "KRAFT",
                "titel", "Manipuliert",
                "startZeit", "2026-11-19T18:00:00Z"
        );

        // Nutzer B sieht in seiner Liste keines der Workouts von Nutzer A
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        // Nutzer B: privates Workout von A -> GET/PUT/DELETE einheitlich 404 WORKOUT_NICHT_GEFUNDEN
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts/" + privatesWorkoutId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(fremdUpdate)
                .when().put("/v1/workouts/" + privatesWorkoutId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().delete("/v1/workouts/" + privatesWorkoutId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        // Nicht existierende UUID liefert exakt dasselbe 404 WORKOUT_NICHT_GEFUNDEN
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        // Nutzer B (USER) darf öffentliches Workout lesen, aber weder ändern noch löschen
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId)
                .then()
                .statusCode(200)
                .body("titel", equalTo("Öffentliches Training A"));

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(fremdUpdate)
                .when().put("/v1/workouts/" + oeffentlichesWorkoutId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().delete("/v1/workouts/" + oeffentlichesWorkoutId)
                .then()
                .statusCode(404)
                .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

        // ADMIN darf KEINE fremden Workouts (weder privat noch öffentlich) lesen, ändern oder löschen!
        for (String fremdeId : List.of(privatesWorkoutId, oeffentlichesWorkoutId)) {
            given()
                    .cookie("quarkus-credential", admin.sessionCookie())
                    .when().get("/v1/workouts/" + fremdeId)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", admin.sessionCookie())
                    .body(fremdUpdate)
                    .when().put("/v1/workouts/" + fremdeId)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));

            given()
                    .cookie("quarkus-credential", admin.sessionCookie())
                    .when().delete("/v1/workouts/" + fremdeId)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("WORKOUT_NICHT_GEFUNDEN"));
        }
    }

    @Test
    void unauthentifizierteWorkoutAnfragenLiefern401FehlerV1JsonOhneRedirect() {
        String zufallsId = UUID.randomUUID().toString();

        given()
                .redirects().follow(false)
                .when().get("/v1/workouts")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("Location", nullValue())
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .contentType(ContentType.JSON)
                .body(Map.of("typ", "KRAFT", "titel", "Test", "startZeit", "2026-11-19T18:00:00Z"))
                .when().post("/v1/workouts")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .when().get("/v1/workouts/" + zufallsId)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .contentType(ContentType.JSON)
                .body(Map.of("version", 0, "typ", "KRAFT", "titel", "Test", "startZeit", "2026-11-19T18:00:00Z"))
                .when().put("/v1/workouts/" + zufallsId)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));

        given()
                .redirects().follow(false)
                .when().delete("/v1/workouts/" + zufallsId)
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }

    @ApplicationScoped
    static class RollenTestHelfer {

        @Inject
        DataSource dataSource;

        @Transactional
        void setzeRolleAdmin(UUID benutzerId) {
            try (Connection verbindung = dataSource.getConnection();
                 PreparedStatement stmt = verbindung.prepareStatement(
                         "UPDATE benutzer SET rolle = 'ADMIN' WHERE id = ?")) {
                stmt.setObject(1, benutzerId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
