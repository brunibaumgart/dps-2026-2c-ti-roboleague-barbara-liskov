package com.roboleague.usecase;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookAssembly;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.scheme.RoundSelection;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Publishes the next version of a challenge's rulebook. Earlier versions stay as they were. The new version applies
 * to what is played after it: a category that already has results keeps the version that scored them, and one that
 * does not will play with the new version, so the new version has to admit the rounds already scheduled for it.
 */
public class PublishRulebookUseCase {
    private final ChallengeRepository challengeRepository;
    private final RoundRepository roundRepository;
    private final CategoryRulebook categoryRulebook;
    private final RuleCatalog catalog;

    public PublishRulebookUseCase(ChallengeRepository challengeRepository, RoundRepository roundRepository,
                                  AttemptRepository attemptRepository, RuleCatalog catalog) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.categoryRulebook = new CategoryRulebook(roundRepository,
                Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null"));
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public Publication<Rulebook> execute(ChallengeId challengeId, RulebookDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge", challengeId));
        return switch (catalog.assemble(definition)) {
            case RulebookAssembly.Rejected rejected -> new Publication.Rejected<>(rejected.problems());
            case RulebookAssembly.Assembled assembled -> {
                List<String> problems = scheduledRoundsOutside(challenge, assembled.ranking().roundSelection());
                if (!problems.isEmpty()) {
                    yield new Publication.Rejected<>(problems);
                }
                Rulebook rulebook = challenge.publish(assembled.scoring(), assembled.ranking());
                challengeRepository.save(challenge);
                yield new Publication.Published<>(rulebook);
            }
        };
    }

    /**
     * The rounds already scheduled in categories without results, which would play with the new version, that its
     * round selection does not admit.
     */
    private List<String> scheduledRoundsOutside(Challenge challenge, RoundSelection selection) {
        List<String> problems = new ArrayList<>();
        TreeSet<String> categories = new TreeSet<>();
        roundRepository.findByChallengeId(challenge.getId()).forEach(round -> categories.add(round.getCategoryId().value()));
        for (String category : categories) {
            List<Round> rounds = categoryRulebook.roundsOf(challenge, CategoryId.of(category));
            if (!categoryRulebook.attemptsOf(rounds).isEmpty()) {
                continue;
            }
            for (Round round : rounds) {
                if (!selection.allowsRound(round.getRoundNumber())) {
                    problems.add("round " + round.getRoundNumber() + " of category " + category
                            + " is already scheduled, and the new version counts " + selection.describe());
                }
            }
        }
        return problems;
    }
}
