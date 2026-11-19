package de.trainingsapp.infrastructure.workout;

import de.trainingsapp.domain.benutzer.BenutzerId;
import de.trainingsapp.domain.workout.Herkunft;
import de.trainingsapp.domain.workout.Satz;
import de.trainingsapp.domain.workout.Uebung;
import de.trainingsapp.domain.workout.Workout;
import de.trainingsapp.domain.workout.WorkoutId;
import de.trainingsapp.domain.workout.WorkoutRepository;
import de.trainingsapp.domain.workout.WorkoutTyp;
import de.trainingsapp.domain.workout.WorkoutVersionskonfliktException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheWorkoutRepository
        implements WorkoutRepository, PanacheRepositoryBase<WorkoutEntity, UUID> {

    @Override
    public Optional<Workout> findeNachId(WorkoutId id) {
        WorkoutEntity entity = findById(id.wert());
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(ladeVollstaendig(List.of(entity)).getFirst());
    }

    @Override
    public List<Workout> findeAlleNachOwner(BenutzerId ownerId, WorkoutTyp optionalerTyp) {
        Sort sort = Sort.descending("startZeit").and("erstelltAm", Sort.Direction.Descending).and("id");
        List<WorkoutEntity> entities = optionalerTyp == null
                ? find("ownerId", sort, ownerId.wert()).list()
                : find("ownerId = ?1 and typ = ?2", sort, ownerId.wert(), optionalerTyp.name()).list();
        return ladeVollstaendig(entities);
    }

    @Override
    public Optional<Workout> findeNachOwnerUndHerkunftUndExterneId(
            BenutzerId ownerId,
            Herkunft herkunft,
            String externeId
    ) {
        return find("ownerId = ?1 and herkunft = ?2 and externeId = ?3",
                ownerId.wert(), herkunft.name(), externeId)
                .firstResultOptional()
                .map(entity -> ladeVollstaendig(List.of(entity)).getFirst());
    }

    @Override
    public void speichern(Workout workout) {
        persist(WorkoutEntity.ausDomain(workout));
        speichereUebungenUndSaetze(workout);
        flush();
    }

    @Override
    public Workout aktualisieren(Workout aktualisiertesWorkout, long erwarteteVersion) {
        int aktualisiert = update(
                "typ = ?1, titel = ?2, startZeit = ?3, dauerSekunden = ?4, notiz = ?5, "
                        + "sichtbarkeit = ?6, geaendertAm = ?7, version = version + 1 "
                        + "where id = ?8 and version = ?9",
                aktualisiertesWorkout.typ().name(),
                aktualisiertesWorkout.titel(),
                aktualisiertesWorkout.startZeit(),
                aktualisiertesWorkout.dauerSekunden(),
                aktualisiertesWorkout.notiz(),
                aktualisiertesWorkout.sichtbarkeit().name(),
                aktualisiertesWorkout.geaendertAm(),
                aktualisiertesWorkout.id().wert(),
                erwarteteVersion
        );
        if (aktualisiert == 0) {
            throw new WorkoutVersionskonfliktException();
        }

        UUID workoutId = aktualisiertesWorkout.id().wert();
        loescheUebungenUndSaetze(workoutId);
        speichereUebungenUndSaetze(aktualisiertesWorkout);
        flush();

        WorkoutEntity gecacht = getEntityManager().find(WorkoutEntity.class, workoutId);
        if (gecacht != null) {
            getEntityManager().refresh(gecacht);
        }

        return aktualisiertesWorkout;
    }

    @Override
    public boolean loeschen(WorkoutId id) {
        loescheUebungenUndSaetze(id.wert());
        boolean geloescht = deleteById(id.wert());
        flush();
        return geloescht;
    }

    private void speichereUebungenUndSaetze(Workout workout) {
        UUID workoutId = workout.id().wert();
        for (Uebung uebung : workout.uebungen()) {
            getEntityManager().persist(UebungEntity.ausDomain(workoutId, uebung));
            UUID uebungId = uebung.id().wert();
            for (Satz satz : uebung.saetze()) {
                getEntityManager().persist(SatzEntity.ausDomain(uebungId, satz));
            }
        }
    }

    private void loescheUebungenUndSaetze(UUID workoutId) {
        getEntityManager().createQuery(
                        "DELETE FROM SatzEntity s WHERE s.uebungId IN "
                                + "(SELECT u.id FROM UebungEntity u WHERE u.workoutId = :workoutId)")
                .setParameter("workoutId", workoutId)
                .executeUpdate();
        getEntityManager().createQuery("DELETE FROM UebungEntity u WHERE u.workoutId = :workoutId")
                .setParameter("workoutId", workoutId)
                .executeUpdate();
    }

    private List<Workout> ladeVollstaendig(List<WorkoutEntity> workoutEntities) {
        if (workoutEntities.isEmpty()) {
            return List.of();
        }
        List<UUID> workoutIds = workoutEntities.stream().map(w -> w.id).toList();
        List<UebungEntity> uebungEntities = getEntityManager().createQuery(
                        "SELECT u FROM UebungEntity u WHERE u.workoutId IN :workoutIds ORDER BY u.position ASC, u.id ASC",
                        UebungEntity.class)
                .setParameter("workoutIds", workoutIds)
                .getResultList();

        Map<UUID, List<Satz>> saetzeNachUebungId = new HashMap<>();
        if (!uebungEntities.isEmpty()) {
            List<UUID> uebungIds = uebungEntities.stream().map(u -> u.id).toList();
            List<SatzEntity> satzEntities = getEntityManager().createQuery(
                            "SELECT s FROM SatzEntity s WHERE s.uebungId IN :uebungIds ORDER BY s.position ASC, s.id ASC",
                            SatzEntity.class)
                    .setParameter("uebungIds", uebungIds)
                    .getResultList();
            for (SatzEntity satzEntity : satzEntities) {
                saetzeNachUebungId
                        .computeIfAbsent(satzEntity.uebungId, ignored -> new ArrayList<>())
                        .add(satzEntity.zuDomain());
            }
        }

        Map<UUID, List<Uebung>> uebungenNachWorkoutId = new HashMap<>();
        for (UebungEntity uebungEntity : uebungEntities) {
            List<Satz> saetze = saetzeNachUebungId.getOrDefault(uebungEntity.id, List.of());
            uebungenNachWorkoutId
                    .computeIfAbsent(uebungEntity.workoutId, ignored -> new ArrayList<>())
                    .add(uebungEntity.zuDomain(saetze));
        }

        return workoutEntities.stream()
                .map(entity -> entity.zuDomain(uebungenNachWorkoutId.getOrDefault(entity.id, List.of())))
                .toList();
    }
}
