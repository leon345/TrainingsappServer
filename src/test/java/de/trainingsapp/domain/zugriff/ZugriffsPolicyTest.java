package de.trainingsapp.domain.zugriff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.passkey.Passkey;
import de.trainingsapp.domain.passkey.PasskeyBezeichnung;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Sichtbarkeit;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutTyp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZugriffsPolicyTest {

    private final ZugriffsPolicy policy = new ZugriffsPolicy();

    @Test
    void nurBesitzerDarfEigenenPasskeyVerwalten() {
        BenutzerId besitzer = BenutzerId.neu();
        BenutzerId fremder = BenutzerId.neu();
        Passkey passkey = new Passkey(
                PasskeyId.neu(),
                besitzer,
                "cred-1",
                new byte[]{1, 2, 3},
                -7L,
                0L,
                new UUID(0L, 0L),
                PasskeyBezeichnung.standard(1),
                LocalDate.of(2026, 11, 19),
                null
        );

        assertTrue(policy.darfPasskeyVerwalten(besitzer, passkey));
        assertFalse(policy.darfPasskeyVerwalten(fremder, passkey));
        assertFalse(policy.darfPasskeyVerwalten(null, passkey));
        assertFalse(policy.darfPasskeyVerwalten(besitzer, null));
    }

    @Test
    void trainingsdatenLesenErlaubtBesitzerOeffentlichFuerUserOderEingeladenAberNieFremdenAdmin() {
        BenutzerId besitzer = BenutzerId.neu();
        BenutzerId andererUser = BenutzerId.neu();
        BenutzerId admin = BenutzerId.neu();

        // Besitzer darf eigene Daten immer lesen
        assertTrue(policy.darfTrainingsdatenLesen(besitzer, Rolle.USER, besitzer, false, false));
        assertTrue(policy.darfTrainingsdatenLesen(besitzer, Rolle.ADMIN, besitzer, false, false));

        // Fremder USER: privat verboten, öffentlich oder eingeladen erlaubt
        assertFalse(policy.darfTrainingsdatenLesen(andererUser, Rolle.USER, besitzer, false, false));
        assertTrue(policy.darfTrainingsdatenLesen(andererUser, Rolle.USER, besitzer, true, false));
        assertTrue(policy.darfTrainingsdatenLesen(andererUser, Rolle.USER, besitzer, false, true));

        // Fremder ADMIN: KEIN Zugriff auf fremde Trainingsdaten
        assertFalse(policy.darfTrainingsdatenLesen(admin, Rolle.ADMIN, besitzer, false, false));
        assertFalse(policy.darfTrainingsdatenLesen(admin, Rolle.ADMIN, besitzer, true, false));
        assertFalse(policy.darfTrainingsdatenLesen(admin, Rolle.ADMIN, besitzer, false, true));
    }

    @Test
    void trainingsdatenBearbeitenNurFuerBesitzer() {
        BenutzerId besitzer = BenutzerId.neu();
        BenutzerId anderer = BenutzerId.neu();

        assertTrue(policy.darfTrainingsdatenBearbeiten(besitzer, besitzer));
        assertFalse(policy.darfTrainingsdatenBearbeiten(anderer, besitzer));
        assertFalse(policy.darfTrainingsdatenBearbeiten(null, besitzer));
    }

    @Test
    void workoutZugriffPrueftEigentuemerRolleUndSichtbarkeit() {
        BenutzerId besitzer = BenutzerId.neu();
        BenutzerId andererUser = BenutzerId.neu();
        BenutzerId admin = BenutzerId.neu();
        Instant jetzt = Instant.parse("2026-11-19T18:00:00Z");

        Workout privat = new Workout(
                WorkoutId.neu(),
                besitzer,
                WorkoutTyp.CALISTHENICS,
                "Privat",
                jetzt,
                1800,
                null,
                Sichtbarkeit.PRIVAT,
                Herkunft.MANUELL,
                null,
                List.of(),
                jetzt,
                jetzt,
                0L
        );
        Workout oeffentlich = new Workout(
                WorkoutId.neu(),
                besitzer,
                WorkoutTyp.KRAFT,
                "Öffentlich",
                jetzt,
                1800,
                null,
                Sichtbarkeit.OEFFENTLICH,
                Herkunft.MANUELL,
                null,
                List.of(),
                jetzt,
                jetzt,
                0L
        );

        assertTrue(policy.darfWorkoutLesen(besitzer, Rolle.USER, privat));
        assertFalse(policy.darfWorkoutLesen(andererUser, Rolle.USER, privat));
        assertTrue(policy.darfWorkoutLesen(andererUser, Rolle.USER, oeffentlich));
        assertFalse(policy.darfWorkoutLesen(admin, Rolle.ADMIN, privat));
        assertFalse(policy.darfWorkoutLesen(admin, Rolle.ADMIN, oeffentlich));
        assertFalse(policy.darfWorkoutLesen(besitzer, Rolle.USER, null));

        assertTrue(policy.darfWorkoutBearbeiten(besitzer, privat));
        assertFalse(policy.darfWorkoutBearbeiten(andererUser, privat));
        assertFalse(policy.darfWorkoutBearbeiten(andererUser, oeffentlich));
        assertFalse(policy.darfWorkoutBearbeiten(admin, privat));
        assertFalse(policy.darfWorkoutBearbeiten(besitzer, null));
    }
}
