package com.roboleague.repository.jpa;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link ChallengeRepository} on Postgres.
 */
@Repository
public class JpaChallengeRepository implements ChallengeRepository {

    private final SpringDataChallenges jpa;
    private final ChallengeMapper mapper;

    JpaChallengeRepository(SpringDataChallenges jpa, RuleCatalog catalog) {
        this.jpa = jpa;
        this.mapper = new ChallengeMapper(catalog);
    }

    @Override
    public void save(Challenge challenge) {
        jpa.save(mapper.toEntity(challenge));
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }
}
