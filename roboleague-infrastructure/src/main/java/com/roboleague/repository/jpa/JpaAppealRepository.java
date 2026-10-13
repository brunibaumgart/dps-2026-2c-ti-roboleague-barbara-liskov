package com.roboleague.repository.jpa;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Driven adapter: implements the domain port {@link AppealRepository} on Postgres.
 */
@Repository
public class JpaAppealRepository implements AppealRepository {

    private final SpringDataAppeals jpa;
    /**
     * The stored version each loaded appeal was read with, so two reviews or resolutions of the same appeal at the
     * same time cannot both win: Postgres refuses the second write and it surfaces in {@link #save}. Same mechanism
     * as {@link JpaAttemptRepository}.
     */
    private final Map<Appeal, Long> loadedVersions = Collections.synchronizedMap(new WeakHashMap<>());

    JpaAppealRepository(SpringDataAppeals jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Appeal appeal) {
        AppealJpaEntity entity = AppealMapper.toEntity(appeal);
        entity.version = loadedVersions.get(appeal);
        AppealJpaEntity saved = WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Appeal " + appeal.getAppealId()
                + " was changed by someone else while it was being updated; load it again and retry");
        loadedVersions.put(appeal, saved.version);
    }

    @Override
    public Optional<Appeal> findById(AppealId appealId) {
        return jpa.findById(appealId.value()).map(this::toDomain);
    }

    @Override
    public List<Appeal> findByAttemptId(AttemptId attemptId) {
        return jpa.findByAttemptId(attemptId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appeal> findPendingAppeals() {
        return jpa.findByStatus("PENDING").stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appeal> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private Appeal toDomain(AppealJpaEntity entity) {
        Appeal appeal = AppealMapper.toDomain(entity);
        loadedVersions.put(appeal, entity.version);
        return appeal;
    }
}
