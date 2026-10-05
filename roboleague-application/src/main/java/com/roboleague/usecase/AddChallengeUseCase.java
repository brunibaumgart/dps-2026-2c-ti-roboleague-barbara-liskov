package com.roboleague.usecase;

import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.RulebookAssembly;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Challenge;

import java.util.Objects;

/**
 * Adds a challenge to an edition by publishing its first rulebook. A definition with problems is rejected,
 * not thrown: whoever sent it gets every problem back.
 */
public class AddChallengeUseCase {
    private final EditionRepository editionRepository;
    private final ChallengeRepository challengeRepository;
    private final RuleCatalog catalog;

    public AddChallengeUseCase(EditionRepository editionRepository, ChallengeRepository challengeRepository,
                               RuleCatalog catalog) {
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public Publication<Challenge> execute(AddChallengeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Challenge.Draft draft = command.draft();
        if (editionRepository.findById(draft.editionId()).isEmpty()) {
            throw new IllegalArgumentException("Edition not found: " + draft.editionId());
        }
        if (challengeRepository.findById(draft.id()).isPresent()) {
            throw new IllegalStateException("Challenge already exists: " + draft.id());
        }
        return switch (catalog.assemble(command.rulebook())) {
            case RulebookAssembly.Rejected rejected -> new Publication.Rejected<>(rejected.problems());
            case RulebookAssembly.Assembled assembled -> {
                Challenge challenge = draft.publish(assembled.scoring(), assembled.ranking());
                challengeRepository.save(challenge);
                yield new Publication.Published<>(challenge);
            }
        };
    }
}
