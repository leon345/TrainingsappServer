package de.trainingsapp.infrastructure.passkey;

import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Macht WebAuthn-Challenges einmalig verwendbar (S-04, B1). Die Extension haelt die Challenge nur
 * im verschluesselten Cookie; ohne Server-Zustand liesse sich eine abgefangene Antwort samt Cookie
 * bis zum Ablauf des Cookies erneut einreichen. Bei Passkeys mit Signaturzaehler 0 faengt das auch
 * die Zaehlerpruefung nicht ab. Gespeichert wird nur der SHA-256-Hash der Challenge, bis zum Ablauf
 * des Challenge-Cookies ({@code quarkus.webauthn.session-timeout}).
 *
 * <p>E26: Der Verbrauch laeuft in einer eigenen Transaktion, ausserhalb der Use-Case-Transaktion.
 */
@ApplicationScoped
public class VerbrauchteChallenges {

    private final Duration gueltigkeit;
    private final Clock uhr = Clock.systemUTC();

    VerbrauchteChallenges(@ConfigProperty(name = "quarkus.webauthn.session-timeout", defaultValue = "30M")
                          Duration gueltigkeit) {
        this.gueltigkeit = gueltigkeit;
    }

    /**
     * @return {@code true}, wenn die Challenge erstmals verbraucht wurde; {@code false}, wenn sie schon
     *         verwendet war
     */
    @Transactional(TxType.REQUIRES_NEW)
    public boolean verbrauche(String challenge) {
        // Erst beim Aufruf holen: In Tests ohne Datenbank ist Hibernate inaktiv.
        EntityManager em = Panache.getEntityManager();
        var jetzt = uhr.instant();
        em.createNativeQuery("DELETE FROM verbrauchte_challenge WHERE gueltig_bis <= ?1")
                .setParameter(1, jetzt)
                .executeUpdate();
        int neu = em.createNativeQuery("""
                        INSERT INTO verbrauchte_challenge (id, challenge_hash, gueltig_bis)
                        VALUES (?1, ?2, ?3) ON CONFLICT (challenge_hash) DO NOTHING""")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, sha256(challenge))
                .setParameter(3, jetzt.plus(gueltigkeit).plus(Duration.ofMinutes(1)))
                .executeUpdate();
        return neu == 1;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
