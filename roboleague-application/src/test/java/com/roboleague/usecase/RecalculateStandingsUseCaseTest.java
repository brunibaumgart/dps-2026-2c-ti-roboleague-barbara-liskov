package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
import com.roboleague.repository.memory.InMemoryStandingsRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * F1 through the use case: with a best-N-of-M rulebook, each entry of the calculated table says which rounds
 * counted, which were discarded and under which rule.
 */
class RecalculateStandingsUseCaseTest {
    private static final JudgeId JUDGE = JudgeId.of("j-1");

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final InMemoryStandingsRepository standings = new InMemoryStandingsRepository();
    private final RecalculateStandingsUseCase recalculate = new RecalculateStandingsUseCase(
            new CategoryResultsReader(challenges, editions, teams, rounds, attempts), standings, CLOCK);
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());

    private final Category maze = category("maze", 1, 4, 14, 25, 2000);
    private final Edition edition = edition("ed-1", DATE, maze);
    private final Challenge bestOfTwo = Challenge.draft(ChallengeId.of("ch-best"), edition.getId(), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                    new RankingScheme(new BestNOfM(1, 2), List.of(new HigherTotal())));
    private final StandingsId mazeStandings = new StandingsId(bestOfTwo.getId(), maze.id());

    RecalculateStandingsUseCaseTest() {
        editions.save(edition);
        RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
        register.execute(edition.getId(), maze.id(), team("team-a"));
        register.execute(edition.getId(), maze.id(), team("team-b"));
        challenges.save(bestOfTwo);
        rounds.save(round("r-1", 1, "team-a", "team-b"));
        rounds.save(round("r-2", 2, "team-a"));
    }

    @Test
    void givenBestOneOfTwoThenEachEntryKeepsItsBestRoundAndExplainsTheOneDiscarded() {
        score("r-1", "team-a", 2);
        score("r-2", "team-a", 5);
        score("r-1", "team-b", 3);

        Standings calculated = recalculate.execute(mazeStandings);

        StandingsEntry first = calculated.latest().table().entries().getFirst();
        assertThat(first.team().teamId()).isEqualTo(TeamId.of("team-a"));
        assertThat(first.total()).isEqualTo(Points.of(100));
        assertThat(first.rounds().selectionRule()).isEqualTo("mejores 1 de 2 rondas");
        assertThat(first.rounds().considered()).containsExactly(
                new RoundResult(RoundId.of("r-2"), attemptIn("r-2", "team-a"), Points.of(100)));
        assertThat(first.rounds().discarded()).containsExactly(
                new RoundResult(RoundId.of("r-1"), attemptIn("r-1", "team-a"), Points.of(40)));
        StandingsEntry second = calculated.latest().table().entries().get(1);
        assertThat(second.total()).isEqualTo(Points.of(60));
        assertThat(second.rounds().discarded()).isEmpty();
    }

    @Test
    void givenARecalculationThenTheStoredStandingsGetANewVersionAndKeepThePreviousOne() {
        score("r-1", "team-a", 2);
        recalculate.execute(mazeStandings);
        score("r-2", "team-a", 5);

        recalculate.execute(mazeStandings);

        Standings stored = standings.findById(mazeStandings).orElseThrow();
        assertThat(stored.versions()).hasSize(2);
        assertThat(stored.version(1).orElseThrow().table().entries().getFirst().total()).isEqualTo(Points.of(40));
        assertThat(stored.latest().table().entries().getFirst().rounds().discarded())
                .extracting(RoundResult::roundId).containsExactly(RoundId.of("r-1"));
    }

    private void score(String roundId, String teamId, int objectives) {
        Reception reception = receive.execute(new ReceiveResultCommand(bestOfTwo.getId(), attemptIn(roundId, teamId),
                new SourceDelivery(new Measurements(new TrackPerformance(60, objectives, 0), 0.0, Map.of()), JUDGE)));
        assertThat(reception).isInstanceOf(Reception.Received.class);
    }

    private static AttemptId attemptIn(String roundId, String teamId) {
        return AttemptId.of(SlotId.of(roundId + "-" + teamId), 1);
    }

    private Round round(String id, int number, String... teamIds) {
        Round round = Round.of(RoundInfo.of(RoundId.of(id), id, RoundScope.of(bestOfTwo.getId(), edition.getId(),
                maze.id(), number)));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-" + id), "Pista", "Madera"),
                List.of(Judge.of(JUDGE, "Juez", "General")));
        for (int index = 0; index < teamIds.length; index++) {
            round = round.addSlot(Slot.of(new SlotIdentity(SlotId.of(id + "-" + teamIds[index]), round.getId(),
                    TeamId.of(teamIds[index])), assignment,
                    new TimeWindow(TIME.plusMinutes(10L * index), TIME.plusMinutes(10L * index + 5))));
        }
        return round;
    }
}
