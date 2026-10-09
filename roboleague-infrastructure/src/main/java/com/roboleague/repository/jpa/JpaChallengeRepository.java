package com.roboleague.repository.jpa;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    @Override
    public List<Challenge> findByEditionId(EditionId editionId) {
        return jpa.findByEditionIdOrderByIdAsc(editionId.value()).stream().map(mapper::toDomain).toList();
    }
}
