package com.roboleague.usecase;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookAssembly;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.scheme.RoundSelection;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Publishes the next version of a challenge's rulebook. Earlier versions stay as they were. A version whose round
 * selection could not count a round the challenge already scheduled is rejected, so the standings can still be
 * calculated with it.
 */
public class PublishRulebookUseCase {
    private final ChallengeRepository challengeRepository;
    private final RoundRepository roundRepository;
    private final RuleCatalog catalog;

    public PublishRulebookUseCase(ChallengeRepository challengeRepository, RoundRepository roundRepository,
                                  RuleCatalog catalog) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public Publication<Rulebook> execute(ChallengeId challengeId, RulebookDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge", challengeId));
        return switch (catalog.assemble(definition)) {
            case RulebookAssembly.Rejected rejected -> new Publication.Rejected<>(rejected.problems());
            case RulebookAssembly.Assembled assembled -> {
                List<String> uncounted = roundsItCannotCount(challengeId, assembled.ranking().roundSelection());
                if (!uncounted.isEmpty()) {
                    yield new Publication.Rejected<>(uncounted);
                }
                Rulebook rulebook = challenge.publish(assembled.scoring(), assembled.ranking());
                challengeRepository.save(challenge);
                yield new Publication.Published<>(rulebook);
            }
        };
    }

    private List<String> roundsItCannotCount(ChallengeId challengeId, RoundSelection selection) {
        return roundRepository.findByChallengeId(challengeId).stream()
                .filter(round -> !selection.allowsRound(round.getRoundNumber()))
                .sorted(Comparator.comparing((Round round) -> round.getCategoryId().value())
                        .thenComparingInt(Round::getRoundNumber))
                .map(round -> "round " + round.getRoundNumber() + " of category " + round.getCategoryId()
                        + " is already scheduled and the new rulebook counts " + selection.describe())
                .toList();
    }
}
