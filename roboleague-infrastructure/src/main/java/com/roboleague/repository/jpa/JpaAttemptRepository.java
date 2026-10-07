package com.roboleague.repository.jpa;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.repository.AttemptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Driven adapter: implements the domain port {@link AttemptRepository} on Postgres.
 */
@Repository
public class JpaAttemptRepository implements AttemptRepository {

    private final SpringDataAttempts jpa;
    /**
     * The stored version each loaded attempt was read with. Saving sends it back, so Postgres refuses the write if
     * someone else saved the attempt in between (for example, the other source of a mixed challenge arriving at the
     * same time). The aggregate itself knows nothing about it.
     */
    private final Map<Attempt, Long> loadedVersions = Collections.synchronizedMap(new WeakHashMap<>());

    JpaAttemptRepository(SpringDataAttempts jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Attempt attempt) {
        AttemptJpaEntity entity = AttemptMapper.toEntity(attempt);
        entity.version = loadedVersions.get(attempt);
        try {
            loadedVersions.put(attempt, jpa.save(entity).version);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrent) {
            throw new IllegalStateException("Attempt " + attempt.getId()
                    + " was changed by someone else while it was being updated; load it again and retry");
        }
    }

    @Override
    public Optional<Attempt> findById(AttemptId attemptId) {
        return jpa.findById(attemptId.value()).map(this::toDomain);
    }

    @Override
    public List<Attempt> findByTeamId(String teamId) {
        return jpa.findByTeamId(teamId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attempt> findByRoundId(String roundId) {
        return jpa.findByRoundId(roundId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attempt> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private Attempt toDomain(AttemptJpaEntity entity) {
        Attempt attempt = AttemptMapper.toDomain(entity);
        loadedVersions.put(attempt, entity.version);
        return attempt;
    }
}
