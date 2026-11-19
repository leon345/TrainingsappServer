package de.trainingsapp.infrastructure.zugriff;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.benutzer.Rolle;
import de.trainingsapp.domain.passkey.PasskeyId;
import de.trainingsapp.domain.passkey.PasskeyNichtGefundenException;
import de.trainingsapp.domain.passkey.PasskeyRepository;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutNichtGefundenException;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.zugriff.ZugriffsPolicy;
import io.quarkus.security.PermissionChecker;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Quarkus @PermissionChecker-Adapter, der ABAC-Prüfungen an die Domain-ZugriffsPolicy delegiert.
 */
@ApplicationScoped
public class ZugriffsPermissionChecker {

    private final PasskeyRepository passkeyRepository;
    private final WorkoutRepository workoutRepository;
    private final ZugriffsPolicy zugriffsPolicy;

    public ZugriffsPermissionChecker(
            PasskeyRepository passkeyRepository,
            WorkoutRepository workoutRepository,
            ZugriffsPolicy zugriffsPolicy
    ) {
        this.passkeyRepository = passkeyRepository;
        this.workoutRepository = workoutRepository;
        this.zugriffsPolicy = zugriffsPolicy;
    }

    @PermissionChecker("passkey:verwalten")
    @Blocking
    @Transactional
    public boolean darfPasskeyVerwalten(UUID id, SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || id == null) {
            return false;
        }
        BenutzerId akteur = BenutzerId.ausText(identity.getPrincipal().getName());
        boolean erlaubt = passkeyRepository.findeNachId(new PasskeyId(id))
                .map(passkey -> zugriffsPolicy.darfPasskeyVerwalten(akteur, passkey))
                .orElse(false);
        if (!erlaubt) {
            // Einheitlich 404 für fehlende und fremde Passkeys (verhindert ID-Aufzählung).
            throw new PasskeyNichtGefundenException();
        }
        return true;
    }

    @PermissionChecker("workout:lesen")
    @Blocking
    @Transactional
    public boolean darfWorkoutLesen(UUID id, SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || id == null) {
            return false;
        }
        BenutzerId akteur = BenutzerId.ausText(identity.getPrincipal().getName());
        Rolle rolle = bestimmeRolle(identity);
        boolean erlaubt = workoutRepository.findeNachId(new WorkoutId(id))
                .map(workout -> zugriffsPolicy.darfWorkoutLesen(akteur, rolle, workout))
                .orElse(false);
        if (!erlaubt) {
            // Einheitlich 404 für fehlende und unzugängliche Workouts (verhindert ID-Aufzählung / IDOR-Leak).
            throw new WorkoutNichtGefundenException();
        }
        return true;
    }

    @PermissionChecker("workout:bearbeiten")
    @Blocking
    @Transactional
    public boolean darfWorkoutBearbeiten(UUID id, SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || id == null) {
            return false;
        }
        BenutzerId akteur = BenutzerId.ausText(identity.getPrincipal().getName());
        boolean erlaubt = workoutRepository.findeNachId(new WorkoutId(id))
                .map(workout -> zugriffsPolicy.darfWorkoutBearbeiten(akteur, workout))
                .orElse(false);
        if (!erlaubt) {
            // Einheitlich 404 für fehlende und fremde Workouts (verhindert ID-Aufzählung / IDOR-Leak).
            throw new WorkoutNichtGefundenException();
        }
        return true;
    }

    private Rolle bestimmeRolle(SecurityIdentity identity) {
        if (identity.hasRole(Rolle.ADMIN.name())) {
            return Rolle.ADMIN;
        }
        if (identity.hasRole(Rolle.USER.name())) {
            return Rolle.USER;
        }
        return null;
    }
}
