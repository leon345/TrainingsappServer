package de.trainingsapp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

/** E14: strikte CSP ohne Inline-Skript, auch auf Fehlerantworten. */
@QuarkusTest
class SecurityHeaderTest {

    @Test
    @TestSecurity(user = "00000000-0000-4000-8000-000000000001")
    void cspAufErfolgreicherAntwort() {
        given().get("/v1/info").then().statusCode(200).header("Content-Security-Policy", strikt())
                .header("X-Content-Type-Options", equalTo("nosniff"));
    }

    @Test
    void cspAuf401() {
        given().get("/v1/info").then().statusCode(401).header("Content-Security-Policy", strikt());
    }

    private static Matcher<String> strikt() {
        return allOf(containsString("default-src 'self'"), containsString("script-src 'self'"),
                containsString("object-src 'none'"), containsString("frame-ancestors 'none'"),
                not(containsString("unsafe-inline")), not(containsString("unsafe-eval")));
    }
}
