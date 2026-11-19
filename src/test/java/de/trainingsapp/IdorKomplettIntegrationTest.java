package de.trainingsapp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Vollständige IDOR- & Rollen-Matrix über alle Ressourcen (Passkeys, Timer, Workouts/Übungen/Sätze,
 * Einladungen, Profile, DSGVO) mit zwei USER-Konten (A & B) sowie einem ADMIN-Konto.
 */
@QuarkusTest
class IdorKomplettIntegrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void vollstaendigeIdorPruefungUeberAlleRessourcenFuerUserUndAdmin() throws Exception {
        var nutzerA = TestBenutzerHelfer.registriereBenutzer("idor.a");
        var nutzerB = TestBenutzerHelfer.registriereBenutzer("idor.b");
        var admin = TestBenutzerHelfer.registriereBenutzer("idor.adm");
        setzeRolleAdmin(admin.id().wert());

        // ------------------------------------------------------------------
        // 1. Passkeys: B & ADMIN können Passkeys von A weder sehen, umbenennen noch löschen
        // ------------------------------------------------------------------
        String passkeyAId = given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/passkeys")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .extract().path("[0].id");

        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().get("/v1/passkeys")
                    .then()
                    .statusCode(200)
                    .body("id", not(hasItem(passkeyAId)));

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(Map.of("bezeichnung", "Fremdzugriff"))
                    .when().patch("/v1/passkeys/" + passkeyAId)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));

            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().delete("/v1/passkeys/" + passkeyAId)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("PASSKEY_NICHT_GEFUNDEN"));
        }

        // ------------------------------------------------------------------
        // 2. Timer: A, B und ADMIN sehen strikt nur ihre eigene Einstellung
        // ------------------------------------------------------------------
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 50, "pauseSekunden", 10))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(Map.of("trainingSekunden", 30, "pauseSekunden", 20))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200);

        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(50))
                .body("pauseSekunden", equalTo(10))
                .body("gespeichert", equalTo(true));

        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(30))
                .body("pauseSekunden", equalTo(20))
                .body("gespeichert", equalTo(true));

        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("gespeichert", equalTo(false));

        // ------------------------------------------------------------------
        // 3. Workouts / Übungen / Sätze: privat & öffentlich
        // ------------------------------------------------------------------
        String privatesWorkoutId = erstelleWorkout(nutzerA.sessionCookie(), "A Privat");
        String oeffentlichesWorkoutId = erstelleWorkout(nutzerA.sessionCookie(), "A Oeffentlich");

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                .then()
                .statusCode(200)
                .body("sichtbarkeit", equalTo("OEFFENTLICH"));

        // B und ADMIN sehen in ihrer eigenen Workout-Liste keines von A's Workouts
        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().get("/v1/workouts")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(0));
        }

        // Privates Workout von A: weder B noch ADMIN dürfen lesen, ändern, Sichtbarkeit umschalten oder löschen
        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().get("/v1/workouts/" + privatesWorkoutId)
                    .then()
                    .statusCode(404);

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(aktualisierungsPayload(0, "Manipuliert"))
                    .when().put("/v1/workouts/" + privatesWorkoutId)
                    .then()
                    .statusCode(404);

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(Map.of("sichtbarkeit", "OEFFENTLICH"))
                    .when().patch("/v1/workouts/" + privatesWorkoutId + "/sichtbarkeit")
                    .then()
                    .statusCode(404);

            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().delete("/v1/workouts/" + privatesWorkoutId)
                    .then()
                    .statusCode(404);
        }

        // Öffentliches Workout von A:
        // - USER B darf lesen (Detail & Profil), aber weder ändern, Sichtbarkeit umschalten noch löschen
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId)
                .then()
                .statusCode(200)
                .body("id", equalTo(oeffentlichesWorkoutId));

        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/profile/" + nutzerA.id().alsText() + "/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(oeffentlichesWorkoutId));

        // - ADMIN darf selbst öffentliches Workout von A weder über Detail noch Profil lesen
        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/workouts/" + oeffentlichesWorkoutId)
                .then()
                .statusCode(404);

        given()
                .cookie("quarkus-credential", admin.sessionCookie())
                .when().get("/v1/profile/" + nutzerA.id().alsText() + "/workouts")
                .then()
                .statusCode(403);

        // - Weder B noch ADMIN dürfen öffentliches Workout von A ändern, Sichtbarkeit umschalten oder löschen
        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(aktualisierungsPayload(1, "Fremd geändert"))
                    .when().put("/v1/workouts/" + oeffentlichesWorkoutId)
                    .then()
                    .statusCode(404);

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(Map.of("sichtbarkeit", "PRIVAT"))
                    .when().patch("/v1/workouts/" + oeffentlichesWorkoutId + "/sichtbarkeit")
                    .then()
                    .statusCode(404);

            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().delete("/v1/workouts/" + oeffentlichesWorkoutId)
                    .then()
                    .statusCode(404);
        }

        // ------------------------------------------------------------------
        // 4. Einladungen: B & ADMIN können auf A's Workout weder auflisten, anlegen noch löschen
        // ------------------------------------------------------------------
        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().get("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                    .then()
                    .statusCode(404);

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(Map.of("benutzername", nutzerB.benutzername()))
                    .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                    .then()
                    .statusCode(404);

            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().delete("/v1/workouts/" + privatesWorkoutId + "/einladungen/" + nutzerB.id().alsText())
                    .then()
                    .statusCode(404);
        }

        // Auch nachdem A tatsächlich B eingeladen hat, darf weder B noch ADMIN Einladungen auflisten/anlegen/löschen
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("benutzername", nutzerB.benutzername()))
                .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                .then()
                .statusCode(201);

        for (String fremdeSession : List.of(nutzerB.sessionCookie(), admin.sessionCookie())) {
            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().get("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                    .then()
                    .statusCode(404);

            given()
                    .contentType(ContentType.JSON)
                    .cookie("quarkus-credential", fremdeSession)
                    .body(Map.of("benutzername", admin.benutzername()))
                    .when().post("/v1/workouts/" + privatesWorkoutId + "/einladungen")
                    .then()
                    .statusCode(404);

            given()
                    .cookie("quarkus-credential", fremdeSession)
                    .when().delete("/v1/workouts/" + privatesWorkoutId + "/einladungen/" + nutzerB.id().alsText())
                    .then()
                    .statusCode(404);
        }

        // ------------------------------------------------------------------
        // 5. DSGVO: Export & Kontolöschung betreffen strikt nur den angemeldeten Benutzer
        // ------------------------------------------------------------------
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/dsgvo/export")
                .then()
                .statusCode(200)
                .body("benutzer.id", equalTo(nutzerB.id().alsText()))
                .body("benutzer.benutzername", equalTo(nutzerB.benutzername()))
                .body("timerEinstellung.trainingSekunden", equalTo(30))
                .body("timerEinstellung.pauseSekunden", equalTo(20))
                .body("workouts", hasSize(0))
                .body("erhalteneEinladungen", hasSize(1))
                .body("erhalteneEinladungen[0].workoutId", equalTo(privatesWorkoutId));

        // B löscht sein Konto -> A's Daten bleiben vollständig intakt
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().delete("/v1/konto")
                .then()
                .statusCode(204);

        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/ich")
                .then()
                .statusCode(401);

        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/ich")
                .then()
                .statusCode(200)
                .body("id", equalTo(nutzerA.id().alsText()));

        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/timer/einstellung")
                .then()
                .statusCode(200)
                .body("trainingSekunden", equalTo(50))
                .body("pauseSekunden", equalTo(10));

        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/workouts")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    private String erstelleWorkout(String sessionCookie, String titel) {
        Map<String, Object> payload = Map.of(
                "typ", "CALISTHENICS",
                "titel", titel,
                "startZeit", "2026-11-19T12:00:00Z",
                "dauerSekunden", 1800,
                "uebungen", List.of(
                        Map.of(
                                "name", "Klimmzüge",
                                "variante", "Eng",
                                "saetze", List.of(Map.of("wiederholungen", 10, "pauseSekunden", 90))
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

    private Map<String, Object> aktualisierungsPayload(long version, String titel) {
        return Map.of(
                "version", version,
                "typ", "CALISTHENICS",
                "titel", titel,
                "startZeit", "2026-11-19T12:00:00Z",
                "dauerSekunden", 1500,
                "uebungen", List.of(
                        Map.of(
                                "name", "Dips",
                                "saetze", List.of(Map.of("wiederholungen", 12, "pauseSekunden", 60))
                        )
                )
        );
    }

    private void setzeRolleAdmin(UUID benutzerId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement("UPDATE benutzer SET rolle = 'ADMIN' WHERE id = ?")) {
            stmt.setObject(1, benutzerId);
            stmt.executeUpdate();
        }
    }
}
