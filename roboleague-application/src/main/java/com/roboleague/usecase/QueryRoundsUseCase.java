package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class QueryRoundsUseCase {
    private final ChallengeRepository challenges;
    private final EditionRepository editions;
    private final RoundRepository rounds;
    public QueryRoundsUseCase(ChallengeRepository challenges, EditionRepository editions, RoundRepository rounds) {
        this.challenges = Objects.requireNonNull(challenges);
        this.editions = Objects.requireNonNull(editions);
        this.rounds = Objects.requireNonNull(rounds);
    }

    public Round get(RoundId id) {
        return rounds.findById(id).orElseThrow(() -> new NotFoundException("Round", id));
    }

    /** The round that contains the slot, or NotFoundException("Slot", slotId). One read. */
    public Round bySlot(SlotId slotId) {
        return rounds.findBySlotId(slotId).orElseThrow(() -> new NotFoundException("Slot", slotId));
    }

    /**
     * The rounds with those ids, keyed by id, in one read however many ids there are (none for an empty collection).
     * A stored reference that does not exist is an InconsistentStateException: the caller got the ids from attempts
     * or standings.
     */
    public Map<RoundId, Round> byIds(Collection<RoundId> ids) {
        return StoredReferences.byIds(ids, rounds::findByIds, Round::getId, "Stored round not found");
    }

    public List<Round> list(ChallengeId id, Optional<CategoryId> category) {
        var challenge = challenges.findById(id).orElseThrow(() -> new NotFoundException("Challenge", id));
        var edition = editions.findById(challenge.getEditionId())
                .orElseThrow(() -> new InconsistentStateException("Edition of challenge " + id
                        + " not found: " + challenge.getEditionId()));
        category.ifPresent(edition::category);
        return rounds.findByChallengeId(id).stream()
                .filter(round -> category.isEmpty() || category.get().equals(round.getCategoryId()))
                .sorted(Comparator.comparingInt(Round::getRoundNumber)
                        .thenComparing(round -> round.getCategoryId().value()).thenComparing(round -> round.getId().value()))
                .toList();
    }
}
