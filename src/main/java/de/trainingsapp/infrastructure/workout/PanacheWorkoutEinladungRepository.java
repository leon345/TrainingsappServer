package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.WorkoutEinladung;
import de.trainingsapp.domain.workout.WorkoutEinladungRepository;
import de.trainingsapp.domain.workout.WorkoutEinladungStatus;
import de.trainingsapp.domain.workout.WorkoutId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheWorkoutEinladungRepository
        implements WorkoutEinladungRepository, PanacheRepositoryBase<WorkoutEinladungEntity, UUID> {

    @Override
    public List<WorkoutEinladung> findeAlleNachWorkoutId(WorkoutId workoutId) {
        Sort sort = Sort.descending("erstelltAm").and("id");
        return find("workoutId", sort, workoutId.wert()).stream()
                .map(WorkoutEinladungEntity::zuDomain)
                .toList();
    }

    @Override
    public List<WorkoutEinladung> findeAlleNachWorkoutIds(Collection<WorkoutId> workoutIds) {
        if (workoutIds == null || workoutIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = workoutIds.stream().map(WorkoutId::wert).distinct().toList();
        return getEntityManager().createQuery(
                        "SELECT e FROM WorkoutEinladungEntity e WHERE e.workoutId IN :workoutIds "
                                + "ORDER BY e.erstelltAm DESC, e.id ASC",
                        WorkoutEinladungEntity.class)
                .setParameter("workoutIds", ids)
                .getResultList()
                .stream()
                .map(WorkoutEinladungEntity::zuDomain)
                .toList();
    }

    @Override
    public List<WorkoutEinladung> findeAlleNachEingeladenerId(BenutzerId eingeladenerId) {
        Sort sort = Sort.descending("erstelltAm").and("id");
        return find("eingeladenerId = ?1 and status = ?2", sort,
                eingeladenerId.wert(), WorkoutEinladungStatus.AKTIV.name())
                .stream()
                .map(WorkoutEinladungEntity::zuDomain)
                .toList();
    }

    @Override
    public boolean existiertAktiveEinladung(WorkoutId workoutId, BenutzerId eingeladenerId) {
        return count("workoutId = ?1 and eingeladenerId = ?2 and status = ?3",
                workoutId.wert(), eingeladenerId.wert(), WorkoutEinladungStatus.AKTIV.name()) > 0;
    }

    @Override
    public void speichern(WorkoutEinladung einladung) {
        persist(WorkoutEinladungEntity.ausDomain(einladung));
        flush();
    }

    @Override
    public boolean loeschenNachWorkoutUndEingeladener(WorkoutId workoutId, BenutzerId eingeladenerId) {
        long geloescht = delete("workoutId = ?1 and eingeladenerId = ?2",
                workoutId.wert(), eingeladenerId.wert());
        flush();
        return geloescht > 0;
    }
}
