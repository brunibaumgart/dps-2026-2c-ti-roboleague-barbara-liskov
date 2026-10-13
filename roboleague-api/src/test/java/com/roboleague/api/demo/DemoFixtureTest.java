package com.roboleague.api.demo;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.rules.AbandonedVictimsRule;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsTable;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.StandingsRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(PostgresContainer.class)
class DemoFixtureTest {
    private static final StandingsId MAZE_TABLE = new StandingsId(ChallengeId.of("ch-maze"), CategoryId.of("cat-junior"));
    private static final TeamId CYBER = TeamId.of("t-a");
    private static final TeamId TITAN = TeamId.of("t-b");
    private static final TeamId NOVA = TeamId.of("t-c");
    /** Ids the README cites: they only hold while the demo runs its steps in the same order. */
    private static final List<RoundId> MAZE_ROUNDS = List.of(RoundId.of("demo-1"), RoundId.of("demo-5"),
            RoundId.of("demo-9"), RoundId.of("demo-13"));
    private static final AttemptId APPEALED_ATTEMPT = AttemptId.of(SlotId.of("demo-7"), 1);
    private static final AppealId APPEAL = AppealId.of("demo-43");

    @Autowired
    private StandingsRepository standings;

    @Autowired
    private AppealRepository appeals;

    @Autowired
    private ChallengeRepository challenges;

    @Autowired
    private AttemptRepository attempts;

    @Autowired
    private EditionRepository editions;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DemoFixture demo;

    @Test
    void theDemoUsesFixedDatesSoEveryRunEndsTheSame() {
        assertThat(editions.findById(EditionId.of("ed-1")).orElseThrow().getDates())
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));
    }

    @Test
    void theDemoGivesTheIdsAndTimesTheReadmeCites() {
        assertThat(rounds.findByChallengeId(ChallengeId.of("ch-maze"))).extracting(Round::getId)
                .containsExactlyInAnyOrderElementsOf(MAZE_ROUNDS);
        assertThat(attempts.findById(APPEALED_ATTEMPT).orElseThrow().getTeamId()).isEqualTo(TITAN);
        assertThat(appeals.findById(APPEAL).orElseThrow().getSubmittedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 13, 10));
        assertThat(mazeStandings().official().orElseThrow().calculatedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 13, 30, 1));
    }

    @Test
    void theAppealMovesTitanTeamFromLastToFirstAndThatVersionIsOfficial() {
        assertThat(appeals.findAll()).singleElement().satisfies(appeal -> assertThat(appeal.isAccepted()).isTrue());

        Standings maze = mazeStandings();
        assertThat(maze.versions()).hasSize(2);
        assertThat(maze.official().orElseThrow().number()).isEqualTo(2);
        assertThat(teamsInOrder(maze.version(1).orElseThrow().table())).containsExactly(CYBER, NOVA, TITAN);
        assertThat(teamsInOrder(maze.official().orElseThrow().table())).containsExactly(TITAN, CYBER, NOVA);
        assertThat(maze.official().orElseThrow().table().entries()).extracting(StandingsEntry::total)
                .containsOnly(Points.of(717));
    }

    @Test
    void theAppealLowersWhatTheDeductionsTookFromTitanTeam() {
        Attempt appealed = attempts.findById(APPEALED_ATTEMPT).orElseThrow();
        assertThat(appealed.getRevisionHistory().getFirst().breakdown().totalScore()).isEqualTo(Points.of(197.5));
        assertThat(appealed.getRevisionHistory().getFirst().breakdown().deducted()).isEqualTo(Points.of(65));
        assertThat(appealed.getScoreBreakdown().totalScore()).isEqualTo(Points.of(257.5));
        assertThat(appealed.getScoreBreakdown().deducted()).isEqualTo(Points.of(5));
    }

    @Test
    void bestThreeOfFiveDiscardsTheWorstRoundAndTheAppealChangesWhichOne() {
        StandingsEntry titanBefore = entryOf(mazeStandings().version(1).orElseThrow().table(), TITAN);
        assertThat(titanBefore.rounds().discarded()).extracting(RoundResult::roundId)
                .containsExactly(MAZE_ROUNDS.get(1));
        assertThat(titanBefore.total()).isEqualTo(Points.of(667.5));

        StandingsTable official = mazeStandings().official().orElseThrow().table();
        StandingsEntry titan = entryOf(official, TITAN);
        assertThat(titan.rounds().selectionRule()).isEqualTo("mejores 3 de 5 rondas");
        assertThat(titan.rounds().considered()).hasSize(3);
        assertThat(titan.rounds().discarded()).singleElement().satisfies(round -> {
            assertThat(round.roundId()).isEqualTo(MAZE_ROUNDS.get(3));
            assertThat(round.total()).isEqualTo(Points.of(208));
        });
        assertThat(entryOf(official, CYBER).rounds().discarded()).singleElement().satisfies(round -> {
            assertThat(round.roundId()).isEqualTo(MAZE_ROUNDS.get(3));
            assertThat(round.total()).isEqualTo(Points.of(186.5));
        });
        assertThat(entryOf(official, NOVA).rounds().considered()).hasSize(3);
        assertThat(entryOf(official, NOVA).rounds().discarded()).isEmpty();
    }

    @Test
    void theTiesOnTotalAreBrokenByTheSecondAndThirdCriteriaOfTheChain() {
        assertThat(mazeStandings().official().orElseThrow().table().entries())
                .extracting(entry -> entry.placement().position(), entry -> entry.placement().explanation())
                .containsExactly(
                        tuple(1, "Primer puesto"),
                        tuple(2, "Empata en puntaje con el puesto 1; desempata por Menor tiempo"),
                        tuple(3, "Empata en puntaje con el puesto 2; desempata por "
                                + "Menor descuento por penalizaciones"));
        assertThat(mazeStandings().version(1).orElseThrow().table().entries())
                .extracting(entry -> entry.placement().explanation())
                .containsExactly("Primer puesto",
                        "Empata en puntaje con el puesto 1; desempata por Menor descuento por penalizaciones",
                        "Debajo del puesto 2 por Mayor puntaje total");
    }

    @Test
    void theTeamThatDidNotShowUpHasItsTurnCancelledSoThePublicationWentThrough() {
        Round lastRound = rounds.findById(MAZE_ROUNDS.get(3)).orElseThrow();
        assertThat(lastRound.getSlots()).filteredOn(slot -> slot.getTeamId().equals(NOVA)).singleElement()
                .satisfies(slot -> {
                    assertThat(slot.getStatus()).isEqualTo(Slot.SlotStatus.CANCELLED);
                    assertThat(slot.getCancellation().orElseThrow().reason()).isEqualTo("El equipo no se presentó");
                });
    }

    @Test
    void theDemoConfiguresThreeChallengesThroughTheUseCases() {
        assertThat(challenges.findById(ChallengeId.of("ch-maze"))).isPresent();
        assertThat(challenges.findById(ChallengeId.of("ch-line")).orElseThrow().currentRulebook().version())
                .isEqualTo(new RulebookVersion(2));
        assertThat(challenges.findById(ChallengeId.of("ch-rescue")).orElseThrow().currentRulebook().requiredSources())
                .containsExactlyInAnyOrder(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
    }

    @Test
    void theThreeRulebooksUseTheTwelveRuleTypes() {
        Set<String> types = new TreeSet<>();
        for (RulebookDefinition rulebook : List.of(DemoRulebooks.maze(), DemoRulebooks.lineFollower(Points.of(30)),
                DemoRulebooks.rescue())) {
            RulebookDefinition.Scoring scoring = rulebook.scoring();
            collectTypes(scoring.rules(), types);
            collectTypes(scoring.bonuses().rules(), types);
            collectTypes(scoring.deductions(), types);
        }
        assertThat(types).hasSize(12).containsExactlyInAnyOrder(TimeBasedRule.TYPE, ObjectivesRule.TYPE,
                JudgeSubjectiveRule.TYPE, PrecisionRule.TYPE, VictimsRule.TYPE, CompositeScoreRule.TYPE,
                MilestoneBonusRule.TYPE, AllObjectivesBonusRule.TYPE, PenaltyRule.TYPE, CountedFaultRule.TYPE,
                ResourceConsumptionRule.TYPE, AbandonedVictimsRule.TYPE);
    }

    @Test
    void theMixedChallengeDeclaresWhatTheJudgePanelMeasures() {
        assertThat(challenges.findById(ChallengeId.of("ch-rescue")).orElseThrow().currentRulebook()
                .check(ResultSource.JUDGE_PANEL, Map.of("victimas_rescatadas", 3.0, "rescate_completo", 0.0)))
                .isEqualTo(new MeasurementCheck.Accepted());
    }

    @Test
    void theMazeAttemptsShowTheBonusCapInTheirBreakdown() {
        Attempt cyberFirstRound = attempts.findByRoundId(MAZE_ROUNDS.getFirst()).stream()
                .filter(attempt -> attempt.getTeamId().equals(CYBER)).findFirst().orElseThrow();
        assertThat(cyberFirstRound.getScoreBreakdown().totalScore()).isEqualTo(Points.of(235));
        assertThat(cyberFirstRound.getScoreBreakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal().equals(Points.of(-10)));
        assertThat(attempts.findById(APPEALED_ATTEMPT).orElseThrow().getRevisionHistory().getFirst().breakdown().items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal().equals(Points.of(-35)));
    }

    @Test
    void theMixedRescueHasOneAttemptScoredWithBothSourcesAndOneAwaitingTheJudgePanel() {
        Attempt scored = rescueAttemptOf(CYBER);
        assertThat(scored.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(scored.getDeliveries()).extracting(SourceDelivery::source)
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
        // 3 zones => 45; 3 victims => 75; judges 8 and 7 => 7.5 * 5 = 37.5; one victim abandoned => -10
        assertThat(scored.countableScore()).hasValueSatisfying(score -> assertThat(score.totalScore()).isEqualTo(Points.of(147.5)));

        Attempt awaiting = rescueAttemptOf(TITAN);
        assertThat(awaiting.getStatus()).isEqualTo(Attempt.AttemptStatus.AWAITING_SOURCES);
        assertThat(awaiting.countableScore()).isEmpty();
    }

    /**
     * Loads the demo a second time over an emptied database and compares what the API shows (ids, times, totals,
     * positions) plus the ids and times of every attempt event, which the API does not show.
     */
    @Test
    void loadingTheDemoAgainOverAnEmptyDatabaseEndsInTheSameState() throws Exception {
        String first = state();
        assertThat(first).contains("\"demo-43\"", "2026-11-10T13:10:00");

        flyway.clean();
        flyway.migrate();
        assertThat(attempts.findAll()).isEmpty();
        demo.run(new DefaultApplicationArguments());

        assertThat(state()).isEqualTo(first);
    }

    private String state() throws Exception {
        List<String> paths = new ArrayList<>(List.of("/editions/ed-1", "/editions/ed-1/registrations",
                "/editions/ed-1/challenges", "/challenges/ch-maze/rounds", "/challenges/ch-rescue/rounds",
                "/challenges/ch-maze/standings?categoryId=cat-junior",
                "/challenges/ch-maze/standings/versions/1?categoryId=cat-junior",
                "/challenges/ch-maze/appeals?categoryId=cat-junior"));
        List<Attempt> all = attempts.findAll().stream()
                .sorted(Comparator.comparing(attempt -> attempt.getId().value())).toList();
        StringBuilder state = new StringBuilder();
        for (Attempt attempt : all) {
            paths.add("/attempts/" + attempt.getId().value() + "/breakdown");
            attempt.getEventHistory().forEach(event ->
                    state.append(event.eventId()).append(' ').append(event.timestamp()).append('\n'));
        }
        for (String path : paths) {
            state.append(path).append(": ").append(mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).append('\n');
        }
        return state.toString();
    }

    private static void collectTypes(List<RuleDefinition> rules, Set<String> types) {
        for (RuleDefinition rule : rules) {
            types.add(rule.type());
            collectTypes(rule.arguments().rules(), types);
        }
    }

    private Standings mazeStandings() {
        return standings.findById(MAZE_TABLE).orElseThrow();
    }

    private static List<TeamId> teamsInOrder(StandingsTable table) {
        return table.entries().stream().map(entry -> entry.team().teamId()).toList();
    }

    private static StandingsEntry entryOf(StandingsTable table, TeamId team) {
        return table.entries().stream().filter(entry -> entry.team().teamId().equals(team)).findFirst().orElseThrow();
    }

    private Attempt rescueAttemptOf(TeamId team) {
        return attempts.findByTeamId(team).stream()
                .filter(attempt -> attempt.getRulebookReference().challengeId().equals(ChallengeId.of("ch-rescue")))
                .findFirst()
                .orElseThrow();
    }
}
