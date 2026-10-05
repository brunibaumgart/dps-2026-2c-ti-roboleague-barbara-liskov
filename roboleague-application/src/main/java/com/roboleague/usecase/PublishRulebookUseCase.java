package com.roboleague.usecase;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookAssembly;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * Publishes the next version of a challenge's rulebook. Earlier versions stay as they were.
 */
public class PublishRulebookUseCase {
    private final ChallengeRepository challengeRepository;
    private final RuleCatalog catalog;

    public PublishRulebookUseCase(ChallengeRepository challengeRepository, RuleCatalog catalog) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public Publication<Rulebook> execute(ChallengeId challengeId, RulebookDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return switch (catalog.assemble(definition)) {
            case RulebookAssembly.Rejected rejected -> new Publication.Rejected<>(rejected.problems());
            case RulebookAssembly.Assembled assembled -> {
                Rulebook rulebook = challenge.publish(assembled.scoring(), assembled.ranking());
                challengeRepository.save(challenge);
                yield new Publication.Published<>(rulebook);
            }
        };
    }
}
