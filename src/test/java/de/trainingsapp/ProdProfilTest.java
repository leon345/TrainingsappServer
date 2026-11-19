package de.trainingsapp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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
                "/q/health/ready",
                "/q/webauthn/login-options-challenge",
                "/q/webauthn/register-options-challenge",
                "/q/webauthn/webauthn.js",
                "/q/webauthn/logout"
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
    void systemInfoLiefertImProdProfilOhneAnmeldung401MitSicherheitsHeadern() {
        given()
                .redirects().follow(false)
                .when().get("/v1/info")
                .then()
                .statusCode(401)
                .contentType(containsString("application/json"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("no-referrer"))
                .header("Content-Security-Policy", equalTo("default-src 'self'"))
                .body("code", equalTo("NICHT_ANGEMELDET"));
    }

    @Test
    void prodKonfigurationEnthaeltKeineHartcodiertenZugangsdatenOderSecrets() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            props.load(in);
        }

        assertEquals("${DB_URL}", props.getProperty("%prod.quarkus.datasource.jdbc.url"));
        assertEquals("${DB_USER}", props.getProperty("%prod.quarkus.datasource.username"));
        assertEquals("${DB_PASSWORD}", props.getProperty("%prod.quarkus.datasource.password"));
        assertEquals("${WEBAUTHN_RP_ID}", props.getProperty("%prod.quarkus.webauthn.relying-party.id"));
        assertEquals("${WEBAUTHN_ORIGINS}", props.getProperty("%prod.quarkus.webauthn.origins"));
        assertEquals("${SESSION_ENCRYPTION_KEY}", props.getProperty("%prod.quarkus.http.auth.session.encryption-key"));
        assertEquals("false", props.getProperty("%prod.quarkus.smallrye-openapi.enabled"));
        assertEquals("false", props.getProperty("%prod.quarkus.swagger-ui.enabled"));
        assertEquals("true", props.getProperty("quarkus.management.enabled"));
        assertEquals("true", props.getProperty("quarkus.http.proxy.proxy-address-forwarding"));
        assertEquals("strict", props.getProperty("quarkus.webauthn.cookie-same-site"));
        assertEquals("ES256,RS256", props.getProperty("quarkus.webauthn.public-key-credential-parameters"));
    }
}
