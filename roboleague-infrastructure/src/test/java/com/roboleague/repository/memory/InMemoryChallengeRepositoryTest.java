package com.roboleague.repository.memory;

import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryChallengeRepositoryTest {
    @Test
    void filtersByEditionAndSortsByIdWithoutChangingStoredChallenges() {
        var repository = new InMemoryChallengeRepository();
        for (String id : List.of("b", "a", "other")) {
            repository.save(Challenge.draft(ChallengeId.of(id), EditionId.of(id.equals("other") ? "other" : "edition"), id)
                    .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objectives", 1)), List.of()),
                            new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        }
        assertThat(repository.findByEditionId(EditionId.of("edition"))).extracting(challenge -> challenge.getId().value())
                .containsExactly("a", "b");
        assertThat(repository.findByEditionId(EditionId.of("empty"))).isEmpty();
        assertThat(repository.findById(ChallengeId.of("other"))).isPresent();
    }
}
