package com.roboleague.repository.jpa;

import com.roboleague.repository.RoundRepository;
import com.roboleague.support.ConflictException;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link RoundRepository} on Postgres. Saving replaces the whole round
 * and refuses, like the in-memory adapter, a scope or a slot id already used by another round. Postgres also
 * enforces the scope (rounds_scope_uk); the slot ids live in JSON, so their check is the adapter's query alone.
 * Concurrent writes to the same round are detected as in {@link JpaEditionRepository}. Nothing here keeps two
 * schedulings of different rounds from taking the same track or judge at the same time.
 */
@Repository
public class JpaRoundRepository implements RoundRepository {

    private final SpringDataRounds jpa;

    JpaRoundRepository(SpringDataRounds jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Round round) {
        Objects.requireNonNull(round, "round cannot be null");
        RoundJpaEntity entity = RoundMapper.toEntity(round);
        if (jpa.existsByChallengeIdAndCategoryIdAndRoundNumberAndIdNot(
                entity.challengeId, entity.categoryId, entity.roundNumber, entity.id)) {
            throw new ConflictException("Round scope already exists: " + round.getInfo().scope());
        }
        List<String> slotIds = entity.slots.stream().map(RoundJpaEntity.SlotJson::slotId).toList();
        if (!slotIds.isEmpty() && jpa.existsSlotInAnotherRound(entity.id, slotIds)) {
            throw new ConflictException("Slot id already belongs to another round");
        }
        entity.version = jpa.findById(entity.id).map(read -> read.version).orElse(null);
        WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Round " + round.getId()
                + " was changed by someone else while it was being updated; load it again and retry");
    }

    @Override
    public Optional<Round> findById(RoundId roundId) {
        return jpa.findById(roundId.value()).map(RoundMapper::toDomain);
    }

    @Override
    public Optional<Round> findByScope(RoundScope scope) {
        return jpa.findByChallengeIdAndEditionIdAndCategoryIdAndRoundNumber(scope.challengeId().value(),
                scope.editionId().value(), scope.categoryId().value(), scope.roundNumber()).map(RoundMapper::toDomain);
    }

    @Override
    public List<Round> findByChallengeId(ChallengeId challengeId) {
        return jpa.findByChallengeIdOrderByIdAsc(challengeId.value()).stream().map(RoundMapper::toDomain).toList();
    }

    @Override
    public List<Round> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(RoundMapper::toDomain).toList();
    }

    @Override
    public Optional<Round> findBySlotId(SlotId slotId) {
        return jpa.findBySlotId(slotId.value()).map(RoundMapper::toDomain);
    }
}
