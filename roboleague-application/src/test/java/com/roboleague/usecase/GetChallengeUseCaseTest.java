package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetChallengeUseCaseTest {
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final GetChallengeUseCase query = new GetChallengeUseCase(challenges);

    GetChallengeUseCaseTest() {
        for (String id : List.of("ch-a", "ch-b")) {
            challenges.save(Challenge.draft(ChallengeId.of(id), EditionId.of("ed-1"), id)
                    .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(1))), List.of()),
                            new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        }
    }

    @Test
    void givenRepeatedIdsThenEachChallengeComesOnceKeyedById() {
        assertThat(query.byIds(List.of(ChallengeId.of("ch-b"), ChallengeId.of("ch-a"), ChallengeId.of("ch-b"))))
                .containsOnlyKeys(ChallengeId.of("ch-a"), ChallengeId.of("ch-b"));
    }

    @Test
    void givenAMissingIdThenTheStoredReferenceIsInconsistent() {
        assertThatThrownBy(() -> query.byIds(List.of(ChallengeId.of("ghost"))))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining("ghost");
    }

    @Test
    void givenNoIdsThenTheMapIsEmpty() {
        assertThat(query.byIds(List.of())).isEmpty();
    }

    @Test
    void givenAnUnknownIdThenItIsNotFound() {
        assertThatThrownBy(() -> query.execute(ChallengeId.of("ghost"))).isInstanceOf(NotFoundException.class);
    }
}
