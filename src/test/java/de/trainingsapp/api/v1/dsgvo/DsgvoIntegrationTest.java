package de.trainingsapp.api.v1.dsgvo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.trainingsapp.api.v1.auth.TestBenutzerHelfer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DsgvoIntegrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void dsgvoExportUndVollstaendigeKontoloeschungMitSessionUngueltigkeit() throws Exception {
        // Anonym -> 401
        given().when().get("/v1/dsgvo/export").then().statusCode(401);
        given().when().delete("/v1/konto").then().statusCode(401);

        var nutzerA = TestBenutzerHelfer.registriereBenutzer("dsgvo.a");
        var nutzerB = TestBenutzerHelfer.registriereBenutzer("dsgvo.b");

        // Timer-Einstellung für A speichern
        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("trainingSekunden", 45, "pauseSekunden", 15))
                .when().put("/v1/timer/einstellung")
                .then()
                .statusCode(200);

        // Workout von A erstellen und B einladen
        String workoutAId = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of(
                        "typ", "KRAFT",
                        "titel", "DSGVO Test Workout A",
                        "startZeit", "2026-11-19T10:00:00Z",
                        "dauerSekunden", 2400,
                        "notiz", "Alles exportieren",
                        "uebungen", List.of(
                                Map.of(
                                        "name", "Bankdrücken",
                                        "saetze", List.of(Map.of("wiederholungen", 8, "gewichtKg", 75.0, "pauseSekunden", 120))
                                )
                        )
                ))
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .body(Map.of("benutzername", nutzerB.benutzername()))
                .when().post("/v1/workouts/" + workoutAId + "/einladungen")
                .then()
                .statusCode(201);

        // Workout von B erstellen und A einladen (eingehende Einladung für A)
        String workoutBId = given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(Map.of(
                        "typ", "CALISTHENICS",
                        "titel", "Geteiltes Workout B",
                        "startZeit", "2026-11-19T11:00:00Z",
                        "dauerSekunden", 1200,
                        "uebungen", List.of(
                                Map.of(
                                        "name", "Plank",
                                        "saetze", List.of(Map.of("dauerSekunden", 60, "pauseSekunden", 30))
                                )
                        )
                ))
                .when().post("/v1/workouts")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .body(Map.of("benutzername", nutzerA.benutzername()))
                .when().post("/v1/workouts/" + workoutBId + "/einladungen")
                .then()
                .statusCode(201);

        // 1. Vollständigen DSGVO-Export prüfen
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/dsgvo/export")
                .then()
                .statusCode(200)
                .body("exportiertAm", notNullValue())
                .body("benutzer.id", equalTo(nutzerA.id().alsText()))
                .body("benutzer.benutzername", equalTo(nutzerA.benutzername()))
                .body("benutzer.rolle", equalTo("USER"))
                .body("benutzer.erstelltAm", notNullValue())
                .body("benutzer.einwilligungAm", notNullValue())
                .body("passkeys", hasSize(1))
                .body("passkeys[0].bezeichnung", equalTo("Passkey 1"))
                .body("passkeys[0].publicKey", nullValue())
                .body("passkeys[0].credentialId", nullValue())
                .body("timerEinstellung.trainingSekunden", equalTo(45))
                .body("timerEinstellung.pauseSekunden", equalTo(15))
                .body("timerEinstellung.gespeichert", equalTo(true))
                .body("workouts", hasSize(1))
                .body("workouts[0].id", equalTo(workoutAId))
                .body("workouts[0].titel", equalTo("DSGVO Test Workout A"))
                .body("workouts[0].uebungen", hasSize(1))
                .body("workouts[0].uebungen[0].saetze", hasSize(1))
                .body("workouts[0].einladungen", hasSize(1))
                .body("workouts[0].einladungen[0].eingeladenerBenutzername", equalTo(nutzerB.benutzername()))
                .body("erhalteneEinladungen", hasSize(1))
                .body("erhalteneEinladungen[0].workoutId", equalTo(workoutBId))
                .body("erhalteneEinladungen[0].workoutTitel", equalTo("Geteiltes Workout B"))
                .body("erhalteneEinladungen[0].ownerBenutzername", equalTo(nutzerB.benutzername()));

        // 2. Konto von A sofort löschen -> 204 No Content
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().delete("/v1/konto")
                .then()
                .statusCode(204);

        // 3. Alte Session von A liefert danach sofort 401
        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/ich")
                .then()
                .statusCode(401);

        given()
                .cookie("quarkus-credential", nutzerA.sessionCookie())
                .when().get("/v1/dsgvo/export")
                .then()
                .statusCode(401);

        // 4. Direkt in PostgreSQL prüfen: 0 Restdatensätze für A
        UUID aId = nutzerA.id().wert();
        UUID workoutAUuid = UUID.fromString(workoutAId);
        assertEquals(0, zaehle("SELECT COUNT(*) FROM benutzer WHERE id = ?", aId));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM passkey WHERE benutzer_id = ?", aId));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM timer_einstellung WHERE benutzer_id = ?", aId));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM workout WHERE owner_id = ?", aId));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM uebung WHERE workout_id = ?", workoutAUuid));
        assertEquals(0, zaehle(
                "SELECT COUNT(*) FROM satz s JOIN uebung u ON s.uebung_id = u.id WHERE u.workout_id = ?",
                workoutAUuid
        ));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM workout_einladung WHERE workout_id = ?", workoutAUuid));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM workout_einladung WHERE eingeladener_id = ?", aId));
        assertEquals(0, zaehle("SELECT COUNT(*) FROM ausstehende_registrierung WHERE benutzer_id = ?", aId));

        // 5. Nutzer B und dessen Workout sind unberührt, aber die Einladung an A wurde mitgelöscht
        assertEquals(1, zaehle("SELECT COUNT(*) FROM benutzer WHERE id = ?", nutzerB.id().wert()));
        assertEquals(1, zaehle("SELECT COUNT(*) FROM workout WHERE id = ?", UUID.fromString(workoutBId)));
        given()
                .cookie("quarkus-credential", nutzerB.sessionCookie())
                .when().get("/v1/workouts/" + workoutBId + "/einladungen")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    private long zaehle(String sql, UUID parameter) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
