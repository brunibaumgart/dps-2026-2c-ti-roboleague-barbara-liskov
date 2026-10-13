package com.roboleague.repository.jpa;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link AppealRepository} on Postgres.
 */
@Repository
public class JpaAppealRepository implements AppealRepository {

    private final SpringDataAppeals jpa;

    JpaAppealRepository(SpringDataAppeals jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Appeal appeal) {
        AppealJpaEntity entity = AppealMapper.toEntity(appeal);
        WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Appeal " + appeal.getAppealId()
                + " was created by someone else at the same time; load it again and retry");
    }

    @Override
    public Optional<Appeal> findById(AppealId appealId) {
        return jpa.findById(appealId.value()).map(AppealMapper::toDomain);
    }

    @Override
    public List<Appeal> findByAttemptId(AttemptId attemptId) {
        return jpa.findByAttemptId(attemptId.value()).stream().map(AppealMapper::toDomain).toList();
    }

    @Override
    public List<Appeal> findPendingAppeals() {
        return jpa.findByStatus("PENDING").stream().map(AppealMapper::toDomain).toList();
    }

    @Override
    public List<Appeal> findAll() {
        return jpa.findAll().stream().map(AppealMapper::toDomain).toList();
    }
}
