package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explains an attempt with the rulebook version it is scored with: what is still pending and, once scored, how
 * each source contributed.
 */
public class GetAttemptBreakdownUseCase {
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;

    public GetAttemptBreakdownUseCase(AttemptRepository attemptRepository, ChallengeRepository challengeRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public AttemptBreakdown execute(AttemptId attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt", attemptId));
        RulebookReference scoredWith = attempt.getRulebookReference();
        Rulebook rulebook = challengeRepository.findById(scoredWith.challengeId())
                .flatMap(challenge -> challenge.rulebook(scoredWith.version()))
                .orElseThrow(() -> new InconsistentStateException("Rulebook " + scoredWith + " not found"));
        return explain(attempt, rulebook);
    }

    /**
     * The explained attempts with those ids, keyed by id, in two reads however many attempts or challenges there
     * are: the attempts and their challenges (none for an empty collection). A stored reference that does not exist
     * is an InconsistentStateException.
     */
    public Map<AttemptId, AttemptBreakdown> byIds(Collection<AttemptId> attemptIds) {
        Map<AttemptId, Attempt> found = StoredReferences.byIds(attemptIds, attemptRepository::findByIds,
                Attempt::getId, "Attempt not found");
        List<ChallengeId> challengeIds = found.values().stream()
                .map(attempt -> attempt.getRulebookReference().challengeId()).distinct().toList();
        Map<ChallengeId, Challenge> challenges = StoredReferences.byIds(challengeIds, challengeRepository::findByIds,
                Challenge::getId, "Challenge not found");
        Map<AttemptId, AttemptBreakdown> explained = new LinkedHashMap<>();
        for (Attempt attempt : found.values()) {
            RulebookReference scoredWith = attempt.getRulebookReference();
            Rulebook rulebook = challenges.get(scoredWith.challengeId()).rulebook(scoredWith.version())
                    .orElseThrow(() -> new InconsistentStateException("Rulebook " + scoredWith + " not found"));
            explained.put(attempt.getId(), explain(attempt, rulebook));
        }
        return explained;
    }

    private static AttemptBreakdown explain(Attempt attempt, Rulebook rulebook) {
        return new AttemptBreakdown(attempt, attempt.awaitedSources(rulebook), attempt.contributionsBySource(rulebook));
    }
}
