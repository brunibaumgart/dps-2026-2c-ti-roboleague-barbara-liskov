package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsPublication;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.memory.InMemoryAppealRepository;
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
import com.roboleague.support.ActorId;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class StandingsUseCasesTest {
    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final AuditNote CLOSING = AuditNote.of(ActorId.of("org-1"), "Cierre");

    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final InMemoryAppealRepository appeals = new InMemoryAppealRepository();
    private final InMemoryStandingsRepository standings = new InMemoryStandingsRepository();
    private final CategoryResultsReader results = new CategoryResultsReader(challenges, editions, teams, rounds, attempts);
    private final RecalculateStandingsUseCase recalculate = new RecalculateStandingsUseCase(results, standings, CLOCK);
    private final PublishStandingsUseCase publish = new PublishStandingsUseCase(results, standings, CLOCK);
    private final QueryStandingsUseCase query = new QueryStandingsUseCase(results, standings);
    private final QueryAppealsUseCase queryAppeals = new QueryAppealsUseCase(appeals, attempts, results);
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    private final FileAppealUseCase fileAppeal = new FileAppealUseCase(attempts, appeals, CLOCK, ids());

    private final Category maze = category("maze", 1, 4, 14, 25, 2000);
    private final Category rescue = category("rescue", 1, 4, 14, 25, 2000);
    private final Edition edition = edition("ed-1", DATE, maze, rescue);
    private final Challenge challenge = Challenge.draft(ChallengeId.of("ch-1"), edition.getId(), "Laberinto")
            .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", 20.0)), List.of()),
                    new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    private final StandingsId mazeStandings = new StandingsId(challenge.getId(), maze.id());

    StandingsUseCasesTest() {
        editions.save(edition);
        RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
        register.execute(edition.getId(), maze.id(), team("team-a"));
        register.execute(edition.getId(), maze.id(), team("team-b"));
        register.execute(edition.getId(), rescue.id(), team("team-c"));
        challenges.save(challenge);
        rounds.save(round("r-maze", maze, "team-a", "team-b"));
        rounds.save(round("r-rescue", rescue, "team-c"));
        score("r-maze", "team-a", 2);
        score("r-rescue", "team-c", 5);
    }

    @Test
    @DisplayName("The standings of a category take its registered teams only, even those with no result yet")
    void theStandingsTakeTheRegisteredTeamsOfTheCategory() {
        assertThat(recalculate.execute(mazeStandings).latest().table().entries())
                .extracting(entry -> entry.team().teamId(), StandingsEntry::total)
                .containsExactly(tuple(TeamId.of("team-a"), 40.0), tuple(TeamId.of("team-b"), 0.0));
    }

    @Test
    @DisplayName("Every recalculation stores a new version")
    void everyRecalculationStoresANewVersion() {
        recalculate.execute(mazeStandings);
        score("r-maze", "team-b", 3);

        recalculate.execute(mazeStandings);

        assertThat(standings.findById(mazeStandings).orElseThrow().versions()).hasSize(2);
        assertThat(standings.findById(mazeStandings).orElseThrow().latest().table().entries().getFirst().team().teamId())
                .isEqualTo(TeamId.of("team-b"));
    }

    @Test
    @DisplayName("A challenge that does not exist, or a category its edition does not offer, has no standings")
    void unknownChallengesAndCategoriesHaveNoStandings() {
        assertThatThrownBy(() -> recalculate.execute(new StandingsId(ChallengeId.of("missing"), maze.id())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Challenge not found: missing");
        assertThatThrownBy(() -> query.execute(new StandingsId(challenge.getId(), CategoryId.of("sumo"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not offered");
    }

    @Test
    @DisplayName("A turn without an outcome blocks the publication; once scored and recalculated, it is published")
    void aTurnWithoutAnOutcomeBlocksThePublication() {
        recalculate.execute(mazeStandings);

        assertThat(publish.execute(mazeStandings, 1, CLOSING))
                .isEqualTo(new StandingsPublication.Blocked(List.of("1 turn(s) without an outcome yet")));
        assertThat(standings.findById(mazeStandings).orElseThrow().publications()).isEmpty();

        score("r-maze", "team-b", 3);
        assertThat(publish.execute(mazeStandings, 1, CLOSING))
                .isEqualTo(new StandingsPublication.Blocked(
                        List.of("results changed since version 1 was calculated; recalculate first")));
        recalculate.execute(mazeStandings);

        assertThat(publish.execute(mazeStandings, 2, CLOSING)).isInstanceOf(StandingsPublication.Published.class);
        assertThat(standings.findById(mazeStandings).orElseThrow().official()).isPresent();
    }

    @Test
    @DisplayName("Standings that were never calculated cannot be published")
    void standingsNeverCalculatedCannotBePublished() {
        assertThatThrownBy(() -> publish.execute(mazeStandings, 1, CLOSING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Standings not calculated yet: ch-1/maze");
    }

    @Test
    @DisplayName("The overview says what is open now and whether the latest version is outdated")
    void theOverviewSaysWhatIsOpenAndWhetherTheLatestVersionIsOutdated() {
        StandingsOverview before = query.execute(mazeStandings);
        assertThat(before.standings()).isEmpty();
        assertThat(before.outdated()).isTrue();
        assertThat(before.now().unfinishedTurns()).isEqualTo(1);

        recalculate.execute(mazeStandings);
        assertThat(query.execute(mazeStandings).outdated()).isFalse();

        score("r-maze", "team-b", 1);
        assertThat(query.execute(mazeStandings).outdated()).isTrue();
    }

    @Test
    @DisplayName("Appeals are listed by attempt or by category, oldest first, and only of that category")
    void appealsAreListedByAttemptOrByCategory() {
        AttemptId mazeAttempt = attemptIn("r-maze", "team-a");
        Appeal first = fileAppeal.execute(mazeAttempt, TeamId.of("team-a"), "Tiempo", "Video");
        Appeal second = fileAppeal.execute(mazeAttempt, TeamId.of("team-a"), "Objetivos", "Video");
        fileAppeal.execute(attemptIn("r-rescue", "team-c"), TeamId.of("team-c"), "Otra categoria", "Video");

        assertThat(queryAppeals.ofCategory(mazeStandings)).containsExactly(first, second);
        assertThat(queryAppeals.ofAttempt(mazeAttempt)).containsExactly(first, second);
        assertThat(queryAppeals.get(second.getAppealId())).isSameAs(second);
        assertThatThrownBy(() -> queryAppeals.ofAttempt(AttemptId.of(SlotId.of("missing"), 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Attempt not found: missing-1");
    }

    private void score(String roundId, String teamId, int objectives) {
        Reception reception = receive.execute(new ReceiveResultCommand(challenge.getId(), attemptIn(roundId, teamId),
                new SourceDelivery(new Measurements(new TrackPerformance(60, objectives, 0), 0.0, Map.of()), JUDGE)));
        assertThat(reception).isInstanceOf(Reception.Received.class);
    }

    private static AttemptId attemptIn(String roundId, String teamId) {
        return AttemptId.of(SlotId.of(roundId + "-" + teamId), 1);
    }

    private Round round(String id, Category category, String... teamIds) {
        Round round = Round.of(RoundInfo.of(RoundId.of(id), id, RoundScope.of(challenge.getId(), edition.getId(),
                category.id(), 1)));
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
