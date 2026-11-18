package de.trainingsapp.infrastructure.http;

import static io.restassured.RestAssured.given;

import de.trainingsapp.testhilfe.StellbareUhr;
import de.trainingsapp.testhilfe.TestOrigin;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * E17: Ist die Anwendung direkt erreichbar (der Client ist kein vertrauenswuerdiger Proxy), ignoriert
 * Quarkus einen selbst gesetzten X-Forwarded-For. Der Zaehler haengt an der echten Verbindungsadresse;
 * wechselnde gefaelschte Adressen umgehen das Limit nicht.
 */
@QuarkusTest
@TestProfile(RateLimitOhneVertrauenswuerdigenProxyTest.Profil.class)
class RateLimitOhneVertrauenswuerdigenProxyTest {

    public static class Profil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("trainingsapp.rate-limit.anfragen", "3",
                    "trainingsapp.rate-limit.fenster", "PT10M",
                    // nur ein fremder Proxy ist vertrauenswuerdig, nicht der Testclient 127.0.0.1
                    "quarkus.http.proxy.trusted-proxies", "192.0.2.10");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(StellbareUhr.class);
        }
    }

    @Test
    void gefaelschtesXForwardedForUmgehtDasLimitNicht() {
        for (int i = 0; i < 3; i++) {
            given().header("Origin", TestOrigin.wert()).header("X-Forwarded-For", "203.0.113." + (10 + i))
                    .post("/v1/anmelden/optionen").then().statusCode(200);
        }
        given().header("Origin", TestOrigin.wert()).header("X-Forwarded-For", "203.0.113.99")
                .post("/v1/anmelden/optionen").then().statusCode(429);
    }
}
