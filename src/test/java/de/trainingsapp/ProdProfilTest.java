package de.trainingsapp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ProdProfilTest.ProdTestProfile.class)
class ProdProfilTest {

    public static class ProdTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.smallrye-openapi.enabled", "false",
                    "quarkus.smallrye-openapi.management.enabled", "true",
                    "quarkus.swagger-ui.enabled", "false",
                    "quarkus.management.enabled", "true",
                    "quarkus.management.test-port", "0"
            );
        }
    }

    @Test
    void interneEndpunkteLiefernImProdProfil404AufHauptport() {
        for (String pfad : new String[]{
                "/q/openapi",
                "/q/swagger-ui",
                "/q/swagger-ui/",
                "/q/health",
                "/q/health/live",
                "/q/health/ready"
        }) {
            given()
                    .redirects().follow(false)
                    .when().get(pfad)
                    .then()
                    .statusCode(404)
                    .body(emptyOrNullString())
                    .header("Set-Cookie", nullValue());
        }
    }

    @Test
    void systemInfoBleibtImProdProfilMitSicherheitsHeadernErreichbar() {
        given()
                .when().get("/v1/info")
                .then()
                .statusCode(200)
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Content-Security-Policy", equalTo("default-src 'self'"));
    }

    @Test
    void prodKonfigurationEnthaeltKeineHartcodiertenDbZugangsdaten() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            props.load(in);
        }

        assertEquals("${DB_URL}", props.getProperty("%prod.quarkus.datasource.jdbc.url"));
        assertEquals("${DB_USER}", props.getProperty("%prod.quarkus.datasource.username"));
        assertEquals("${DB_PASSWORD}", props.getProperty("%prod.quarkus.datasource.password"));
        assertEquals("false", props.getProperty("%prod.quarkus.smallrye-openapi.enabled"));
        assertEquals("false", props.getProperty("%prod.quarkus.swagger-ui.enabled"));
        assertEquals("true", props.getProperty("quarkus.management.enabled"));
    }
}
