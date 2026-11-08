package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestAttempt;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotCancellation;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.CLOCK;
import static com.roboleague.support.TestValues.TIME;
import static com.roboleague.support.TestValues.ids;
import static com.roboleague.usecase.RoundFixtures.JUDGE;
import static com.roboleague.usecase.RoundFixtures.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTurnAttemptsOfRoundsTest {
    private static final ChallengeId CHALLENGE = ChallengeId.of("ch-1");
    private static final EditionId EDITION = EditionId.of("ed-1");
    private static final CategoryId MAZE = CategoryId.of("maze");
    private static final CategoryId RESCUE = CategoryId.of("rescue");

    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    // Captures above go to the plain repositories, so the counts below belong to the query alone; one counter per
    // port, so a read of rounds is never mistaken for a read of challenges with the same method name.
    private final ReadCounts roundReads = new ReadCounts();
    private final ReadCounts attemptReads = new ReadCounts();
    private final ReadCounts challengeReads = new ReadCounts();
    private final QueryTurnAttemptsUseCase query = new QueryTurnAttemptsUseCase(
            new CountingRoundRepository(rounds, roundReads), new CountingAttemptRepository(attempts, attemptReads),
            new CountingChallengeRepository(challenges, challengeReads));

    QueryTurnAttemptsOfRoundsTest() {
        ScoringScheme scoring = ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of());
        challenges.save(Challenge.draft(CHALLENGE, EDITION, "Laberinto").publish(scoring,
                new RankingScheme(new AllRounds(), new BestAttempt(2), List.of(new HigherTotal()))));
        rounds.save(round(CHALLENGE, EDITION, MAZE, "r-1", 1, "team-a", "team-b", "team-c"));
        rounds.save(round(CHALLENGE, EDITION, MAZE, "r-2", 2, "team-a", "team-b"));
        rounds.save(round(CHALLENGE, EDITION, RESCUE, "r-rescue", 1, "team-d"));
        rounds.save(rounds.findById(RoundId.of("r-1")).orElseThrow().cancelSlot(SlotId.of("r-1-team-c"),
                new SlotCancellation(ActorId.of("org-1"), "No show", TIME)));
        capture("r-1-team-a", 1);
        capture("r-1-team-a", 2);
        capture("r-1-team-b", 3);
        // Published after the maze results: maze keeps playing with v1, rescue has none and takes the current v2.
        Challenge stored = challenges.findById(CHALLENGE).orElseThrow();
        stored.publish(scoring, new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        challenges.save(stored);
    }

    @Test
    void givenRoundsOfTwoCategoriesThenEachCategoryResolvesItsOwnRulebook() {
        Map<SlotId, TurnResults> turns = query.ofRounds(List.of(RoundId.of("r-1"), RoundId.of("r-2"),
                RoundId.of("r-rescue"), RoundId.of("r-1")));

        assertThat(turns).containsOnlyKeys(SlotId.of("r-1-team-a"), SlotId.of("r-1-team-b"), SlotId.of("r-1-team-c"),
                SlotId.of("r-2-team-a"), SlotId.of("r-2-team-b"), SlotId.of("r-rescue-team-d"));
        TurnResults twice = turns.get(SlotId.of("r-1-team-a"));
        assertThat(twice.attempts()).extracting(attempt -> attempt.getId().number()).containsExactly(1, 2);
        assertThat(twice.acceptsResult()).isFalse();
        assertThat(turns.get(SlotId.of("r-1-team-b")).acceptsResult()).isTrue();
        assertThat(turns.get(SlotId.of("r-1-team-c")).acceptsResult()).isFalse();
        assertThat(turns.get(SlotId.of("r-1-team-c")).attempts()).isEmpty();
        assertThat(turns.get(SlotId.of("r-2-team-a")).acceptsResult()).isTrue();
        assertThat(twice.rulebook().version()).isEqualTo(new RulebookVersion(1));
        assertThat(turns.get(SlotId.of("r-2-team-b")).rulebook().version()).isEqualTo(new RulebookVersion(1));
        assertThat(turns.get(SlotId.of("r-rescue-team-d")).rulebook().version()).isEqualTo(new RulebookVersion(2));
        // Rounds (1) and challenges (1), then the category's rounds (1) and attempts (1) for each of two categories.
        assertThat(totalReads()).isEqualTo(6);
        assertThat(roundReads.of("findByIds")).isEqualTo(1);
        assertThat(challengeReads.of("findByIds")).isEqualTo(1);
        assertThat(roundReads.of("findByChallengeId")).isEqualTo(2);
        assertThat(attemptReads.of("findByRoundIds")).isEqualTo(2);
    }

    @Test
    void givenRoundsOfOneCategoryThenTheBatchCostsFourReadsHoweverManyRounds() {
        query.ofRounds(List.of(RoundId.of("r-1")));
        int one = totalReads();
        query.ofRounds(List.of(RoundId.of("r-1"), RoundId.of("r-2"), RoundId.of("r-1")));

        assertThat(one).isEqualTo(4);
        assertThat(totalReads() - one).isEqualTo(4);
        assertThat(attemptReads.of("findByRoundId")).isZero();
    }

    @Test
    void givenOneSlotThenExecuteAgreesWithOfRounds() {
        TurnResults single = query.execute(SlotId.of("r-1-team-a"));

        // The round by slot (1), then its challenge (1) and the category's rounds (1) and attempts (1); the round
        // found by slot is not read again.
        assertThat(totalReads()).isEqualTo(4);
        assertThat(roundReads.of("findBySlotId")).isEqualTo(1);
        assertThat(roundReads.of("findByIds")).isZero();
        assertThat(challengeReads.of("findByIds")).isEqualTo(1);
        assertThat(single.attempts()).extracting(Attempt::getId).isEqualTo(
                query.ofRounds(List.of(RoundId.of("r-1"))).get(SlotId.of("r-1-team-a")).attempts()
                        .stream().map(Attempt::getId).toList());
        assertThatThrownBy(() -> query.execute(SlotId.of("none")))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.resource()).isEqualTo("Slot"));
    }

    @Test
    void givenAnUnknownRoundThenTheStoredReferenceIsInconsistent() {
        assertThatThrownBy(() -> query.ofRounds(List.of(RoundId.of("r-1"), RoundId.of("ghost"))))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining("ghost");
    }

    @Test
    void givenARoundWhoseChallengeIsGoneThenTheStoredStateIsInconsistent() {
        QueryTurnAttemptsUseCase orphan = new QueryTurnAttemptsUseCase(rounds, attempts,
                new InMemoryChallengeRepository());

        assertThatThrownBy(() -> orphan.ofRounds(List.of(RoundId.of("r-1"))))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining(CHALLENGE.value());
    }

    @Test
    void givenNoRoundsThenTheMapIsEmpty() {
        assertThat(query.ofRounds(List.of())).isEmpty();
    }

    private int totalReads() {
        return roundReads.total() + attemptReads.total() + challengeReads.total();
    }

    private void capture(String slot, int objectives) {
        receive.execute(new ReceiveResultCommand(CHALLENGE, SlotId.of(slot), new SourceDelivery(
                new Measurements(new TrackPerformance(60, objectives, 0), 0.0, Map.of()), JUDGE)));
    }
}
