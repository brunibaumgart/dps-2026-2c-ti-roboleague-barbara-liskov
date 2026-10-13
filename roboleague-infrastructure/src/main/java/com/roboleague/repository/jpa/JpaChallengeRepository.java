package com.roboleague.repository.jpa;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Driven adapter: implements the domain port {@link ChallengeRepository} on Postgres.
 */
@Repository
public class JpaChallengeRepository implements ChallengeRepository {

    private final SpringDataChallenges jpa;
    private final ChallengeMapper mapper;
    /**
     * The stored version each loaded challenge was read with. Publishing appends a rulebook to the loaded instance,
     * so, as in {@link JpaAttemptRepository}, saving sends that version back and Postgres refuses the write if
     * another publication saved the challenge in between, instead of one of the two rulebooks being lost.
     */
    private final Map<Challenge, Long> loadedVersions = Collections.synchronizedMap(new WeakHashMap<>());

    JpaChallengeRepository(SpringDataChallenges jpa, RuleCatalog catalog) {
        this.jpa = jpa;
        this.mapper = new ChallengeMapper(catalog);
    }

    @Override
    public void save(Challenge challenge) {
        ChallengeJpaEntity entity = mapper.toEntity(challenge);
        entity.version = loadedVersions.get(challenge);
        ChallengeJpaEntity saved = WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Challenge "
                + challenge.getId() + " was changed by someone else while it was being updated; load it again and retry");
        loadedVersions.put(challenge, saved.version);
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Challenge> findByEditionId(EditionId editionId) {
        return jpa.findByEditionIdOrderByIdAsc(editionId.value()).stream().map(this::toDomain).toList();
    }

    private Challenge toDomain(ChallengeJpaEntity entity) {
        Challenge challenge = mapper.toDomain(entity);
        loadedVersions.put(challenge, entity.version);
        return challenge;
    }
}
