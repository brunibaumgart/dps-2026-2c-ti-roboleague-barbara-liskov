package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
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
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.roboleague.support.TestValues.CLOCK;
import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.ids;
import static com.roboleague.usecase.RegistrationFixtures.category;
import static com.roboleague.usecase.RegistrationFixtures.edition;
import static com.roboleague.usecase.RegistrationFixtures.team;
import static com.roboleague.usecase.RoundFixtures.JUDGE;
import static com.roboleague.usecase.RoundFixtures.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class QueryTeamResultsUseCaseTest {
    private static final TeamId TEAM_A = TeamId.of("team-a");

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    private final QueryTeamResultsUseCase query =
            new QueryTeamResultsUseCase(teams, challenges, editions, rounds, attempts);

    private final Category maze = category("maze", 1, 4, 14, 25, 2000);
    private final Category rescue = category("rescue", 1, 4, 14, 25, 2000);
    private final Edition edition = edition("ed-1", DATE, maze, rescue);
    private final Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), edition.getId(), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                    new RankingScheme(new AllRounds(), new BestAttempt(2), List.of(new HigherTotal())));

    QueryTeamResultsUseCaseTest() {
        editions.save(edition);
        challenges.save(challenge);
        teams.save(team("team-a"));
        teams.save(team("team-b"));
        rounds.save(round(challenge.getId(), edition.getId(), maze.id(), "r-1", 1, "team-a", "team-b"));
        rounds.save(round(challenge.getId(), edition.getId(), maze.id(), "r-2", 2, "team-a"));
        rounds.save(round(challenge.getId(), edition.getId(), rescue.id(), "r-rescue", 1, "team-a"));
        rounds.save(round(challenge.getId(), edition.getId(), maze.id(), "r-3", 3, "team-b"));
        capture("r-1-team-a", 1);
        capture("r-1-team-a", 2);
        capture("r-rescue-team-a", 3);
    }

    @Test
    void givenNoCategoryThenEveryTurnOfTheTeamComesInRoundOrderWithItsAttemptsInOrder() {
        List<TeamTurn> turns = query.execute(TEAM_A, challenge.getId(), Optional.empty());

        assertThat(turns).extracting(turn -> turn.round().getId().value(), turn -> turn.slot().getSlotId().value())
                .containsExactly(tuple("r-1", "r-1-team-a"), tuple("r-rescue", "r-rescue-team-a"),
                        tuple("r-2", "r-2-team-a"));
        assertThat(turns.getFirst().attempts()).extracting(Attempt::getId).containsExactly(
                AttemptId.of(SlotId.of("r-1-team-a"), 1), AttemptId.of(SlotId.of("r-1-team-a"), 2));
        assertThat(turns.get(1).attempts()).hasSize(1);
        assertThat(turns.getLast().attempts()).isEmpty();
    }

    @Test
    void givenACategoryThenTheRoundsOfOthersAreLeftOut() {
        assertThat(query.execute(TEAM_A, challenge.getId(), Optional.of(maze.id())))
                .extracting(turn -> turn.round().getId()).containsExactly(RoundId.of("r-1"), RoundId.of("r-2"));
    }

    @Test
    void givenARoundWithoutATurnOfTheTeamThenItDoesNotAppear() {
        assertThat(query.execute(TEAM_A, challenge.getId(), Optional.empty()))
                .extracting(turn -> turn.round().getId()).doesNotContain(RoundId.of("r-3"));
        assertThat(query.execute(TeamId.of("team-b"), challenge.getId(), Optional.empty())).hasSize(2);
    }

    @Test
    void givenAnUnknownTeamOrChallengeThenItIsNotFound() {
        assertThatThrownBy(() -> query.execute(TeamId.of("none"), challenge.getId(), Optional.empty()))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.resource()).isEqualTo("Team"));
        assertThatThrownBy(() -> query.execute(TEAM_A, ChallengeId.of("none"), Optional.empty()))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.resource()).isEqualTo("Challenge"));
    }

    @Test
    void givenACategoryTheEditionDoesNotOfferThenItIsRejected() {
        assertThatThrownBy(() -> query.execute(TEAM_A, challenge.getId(), Optional.of(CategoryId.of("sumo"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAChallengeWhoseEditionIsGoneThenTheStoredStateIsInconsistent() {
        QueryTeamResultsUseCase orphan = new QueryTeamResultsUseCase(teams, challenges,
                new InMemoryEditionRepository(), rounds, attempts);

        assertThatThrownBy(() -> orphan.execute(TEAM_A, challenge.getId(), Optional.of(maze.id())))
                .isInstanceOf(InconsistentStateException.class);
    }

    @Test
    void theQueryWritesNothing() {
        int storedAttempts = attempts.findAll().size();
        int storedRounds = rounds.findAll().size();

        query.execute(TEAM_A, challenge.getId(), Optional.empty());

        assertThat(attempts.findAll()).hasSize(storedAttempts);
        assertThat(rounds.findAll()).hasSize(storedRounds);
    }

    private void capture(String slot, int objectives) {
        receive.execute(new ReceiveResultCommand(challenge.getId(), SlotId.of(slot), new SourceDelivery(
                new Measurements(new TrackPerformance(60, objectives, 0), 0.0, Map.of()), JUDGE)));
    }
}
