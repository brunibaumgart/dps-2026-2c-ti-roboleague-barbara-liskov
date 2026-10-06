package com.roboleague.repository.jpa;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.repository.AttemptRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link AttemptRepository} on Postgres.
 */
@Repository
public class JpaAttemptRepository implements AttemptRepository {

    private final SpringDataAttempts jpa;

    JpaAttemptRepository(SpringDataAttempts jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Attempt attempt) {
        jpa.save(AttemptMapper.toEntity(attempt));
    }

    @Override
    public Optional<Attempt> findById(AttemptId attemptId) {
        return jpa.findById(attemptId.value()).map(AttemptMapper::toDomain);
    }

    @Override
    public List<Attempt> findByTeamId(String teamId) {
        return jpa.findByTeamId(teamId).stream().map(AttemptMapper::toDomain).toList();
    }

    @Override
    public List<Attempt> findByRoundId(String roundId) {
        return jpa.findByRoundId(roundId).stream().map(AttemptMapper::toDomain).toList();
    }

    @Override
    public List<Attempt> findAll() {
        return jpa.findAll().stream().map(AttemptMapper::toDomain).toList();
    }
}
