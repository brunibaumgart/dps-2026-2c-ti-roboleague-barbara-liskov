package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Challenge;

import java.util.List;
import java.util.Optional;

/**
 * GraphQL view of a challenge. Its rulebooks are part of the aggregate, so they travel with it and
 * {@code rulebook(version)} filters them without a query. {@code edition}, {@code rounds}, {@code standings} and
 * {@code appeals} are resolved on demand; {@code editionId} stays for the edition batch.
 */
public record ChallengeView(String id, String name, String editionId, RulebookView currentRulebook,
                            List<RulebookView> rulebooks) {

    public static ChallengeView from(Challenge challenge) {
        return new ChallengeView(challenge.getId().value(), challenge.getName(), challenge.getEditionId().value(),
                RulebookView.from(challenge.currentRulebook()),
                challenge.rulebooks().stream().map(RulebookView::from).toList());
    }

    /** The published version with that number; empty when the challenge never published it (not an error). */
    public Optional<RulebookView> rulebook(int version) {
        return rulebooks.stream().filter(rulebook -> rulebook.version() == version).findFirst();
    }
}
