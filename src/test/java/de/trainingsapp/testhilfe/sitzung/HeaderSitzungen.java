package de.trainingsapp.testhilfe.sitzung;

import de.trainingsapp.domain.benutzer.BenutzerId;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NUR TESTQUELLEN (E23, Abnahme 6): serverseitiger Token-Speicher fuer die Header-Sitzung.
 * Mit Ablauf (TTL) und Widerruf; die Uhr ist fuer Tests verstellbar.
 */
@ApplicationScoped
public class HeaderSitzungen {

    public static final String HEADER = "X-Sitzung";
    static final Duration TTL = Duration.ofMinutes(15);

    private record Eintrag(BenutzerId benutzer, Instant ablauf) {
    }

    private final Map<String, Eintrag> tokens = new ConcurrentHashMap<>();
    private final SecureRandom zufall = new SecureRandom();
    private volatile Duration uhrVersatz = Duration.ZERO;

    String erzeuge(BenutzerId benutzer) {
        byte[] roh = new byte[32];
        zufall.nextBytes(roh);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(roh);
        tokens.put(token, new Eintrag(benutzer, jetzt().plus(TTL)));
        return token;
    }

    Optional<BenutzerId> pruefe(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Eintrag eintrag = tokens.get(token);
        if (eintrag == null) {
            return Optional.empty();
        }
        if (!jetzt().isBefore(eintrag.ablauf())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(eintrag.benutzer());
    }

    void widerrufe(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public void verstelleUhr(Duration versatz) {
        this.uhrVersatz = versatz;
    }

    private Instant jetzt() {
        return Instant.now().plus(uhrVersatz);
    }
}
