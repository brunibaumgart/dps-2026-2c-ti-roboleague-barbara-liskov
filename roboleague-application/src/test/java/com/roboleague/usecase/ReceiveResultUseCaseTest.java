package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.repository.memory.InMemoryRoundRepository;
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
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiveResultUseCaseTest {

    private static final ChallengeId MAZE = ChallengeId.of("ch-maze");
    private static final ChallengeId RESCUE = ChallengeId.of("ch-rescue");
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final RankingScheme RANKING = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
    private static final AttemptId TURN = AttemptId.of(SlotId.of("slot-1"), 1);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final SourceDelivery RESCUE_PANEL = new SourceDelivery(
            new JudgeScores(Map.of(JudgeId.of("judge-1"), 8.0, JudgeId.of("judge-2"), 6.0), Map.of(RESCUED.name(), 2.0)), JudgeId.of("judge-2"));

    private InMemoryAttemptRepository attempts;
    private InMemoryChallengeRepository challenges;
    private ReceiveResultUseCase receive;

    @BeforeEach
    void setUp() {
        attempts = new InMemoryAttemptRepository();
        challenges = new InMemoryChallengeRepository();
        challenges.save(Challenge.draft(MAZE, EditionId.of("ed-1"), "Laberinto").publish(mazeScoring(), RANKING));
        challenges.save(Challenge.draft(RESCUE, EditionId.of("ed-1"), "Rescate").publish(new ScoringScheme(
                new MetricSheet(List.of(new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)))),
                new ScoreRules(List.of(new ObjectivesRule("Zonas despejadas", 15.0),
                        new VictimsRule("Víctimas rescatadas", RESCUED, 25.0),
                        new JudgeSubjectiveRule("Panel técnico", 5.0)), List.of(), List.of()),
                new Unlimited()), RANKING));
        InMemoryRoundRepository rounds = new InMemoryRoundRepository();
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1", RoundScope.of(EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)));
        round.addSlot(Slot.of(SlotIdentity.of(SlotId.of("slot-1"), RoundId.of("r-1"), TeamId.of("t-1")),
                SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Madera"),
                        List.of(Judge.of(JudgeId.of("judge-1"), "Juez Uno", "General"), Judge.of(JudgeId.of("judge-2"), "Juez Dos", "General"))),
                new TimeWindow(START, START.plusMinutes(10))));
        rounds.save(round);
        receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    }

    @Test
    @DisplayName("Hallazgo 4: capturar dos veces el mismo turno no borra el valor original")
    void givenACapturedTurnThenASecondCaptureIsRefusedAndTheOriginalStays() {
        receive.execute(new ReceiveResultCommand(MAZE, TURN, sensors(55.0, 4, 0, "judge-1")));

        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, TURN, sensors(90.0, 0, 3, "judge-2"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot receive results");

        Attempt stored = attempts.findById(TURN).orElseThrow();
        assertThat(stored.getRevisionHistory()).hasSize(1);
        assertThat(stored.getOriginalSnapshot().metrics().timeTakenSeconds()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("Hallazgo 4: el intento sale del turno: su ronda y su equipo son los del slot")
    void givenAResultThenTheAttemptBelongsToTheTurnsRoundAndTeam() {
        Reception reception = receive.execute(new ReceiveResultCommand(MAZE, TURN, sensors(55.0, 4, 0, "judge-1")));

        assertThat(reception).isInstanceOfSatisfying(Reception.Received.class, received -> {
            assertThat(received.attempt().getId()).isEqualTo(TURN);
            assertThat(received.attempt().getRoundId()).isEqualTo(RoundId.of("r-1"));
            assertThat(received.attempt().getTeamId()).isEqualTo(TeamId.of("t-1"));
        });
    }

    @Test
    @DisplayName("Hallazgo 4: un turno que no existe no recibe resultados")
    void givenASlotThatDoesNotExistThenTheResultIsRefused() {
        assertThatThrownBy(() -> receive.execute(
                new ReceiveResultCommand(MAZE, AttemptId.of(SlotId.of("slot-9"), 1), sensors(55.0, 4, 0, "judge-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slot not found: slot-9");
    }

    @Test
    @DisplayName("Hallazgo 4: un juez que no está asignado al turno no carga resultados")
    void givenAJudgeNotAssignedToTheSlotThenTheResultIsRejected() {
        Reception reception = receive.execute(new ReceiveResultCommand(MAZE, TURN, sensors(55.0, 4, 0, "judge-9")));

        assertThat(reception).isEqualTo(new Reception.Rejected(List.of("judge judge-9 is not assigned to slot slot-1")));
        assertThat(attempts.findById(TURN)).isEmpty();
    }

    @Test
    @DisplayName("F3: en el desafío mixto el intento queda pendiente hasta que llega el panel de jueces")
    void givenTheMixedChallengeThenTheAttemptAwaitsTheJudgePanelAndThenScores() {
        receive.execute(new ReceiveResultCommand(RESCUE, TURN, sensors(90.0, 3, 0, "judge-1")));
        assertThat(attempts.findById(TURN).orElseThrow().getStatus()).isEqualTo(AttemptStatus.AWAITING_SOURCES);

        receive.execute(new ReceiveResultCommand(RESCUE, TURN, RESCUE_PANEL));

        Attempt scored = attempts.findById(TURN).orElseThrow();
        assertThat(scored.getStatus()).isEqualTo(AttemptStatus.EVALUATED);
        // 3 zones => 45; 2 victims => 50; judges 8 and 6 => average 7 * 5 = 35
        assertThat(scored.getFinalScore()).isEqualTo(130.0);
    }

    @Test
    void givenMeasurementsTheRulebookRejectsThenNothingIsSavedAndTheProblemsComeBack() {
        Reception reception = receive.execute(new ReceiveResultCommand(RESCUE, TURN, new SourceDelivery(
                new JudgeScores(Map.of(JudgeId.of("judge-1"), 8.0), Map.of()), JudgeId.of("judge-1"))));

        assertThat(reception).isEqualTo(new Reception.Rejected(List.of("measurement 'victimas_rescatadas' is missing")));
        assertThat(attempts.findById(TURN)).isEmpty();
    }

    @Test
    void givenAnAttemptOfAnotherChallengeThenItsResultsCannotArriveForThisOne() {
        receive.execute(new ReceiveResultCommand(RESCUE, TURN, sensors(90.0, 3, 0, "judge-1")));

        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, TURN, sensors(90.0, 3, 0, "judge-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Attempt slot-1-1 belongs to challenge ch-rescue, not ch-maze");
    }

    @Test
    @DisplayName("Hallazgo 2: la fuente que llega después de una versión nueva se puntúa con la versión del intento")
    void givenANewRulebookVersionThenAPendingAttemptIsStillScoredWithItsOwn() {
        receive.execute(new ReceiveResultCommand(RESCUE, TURN, sensors(90.0, 3, 0, "judge-1")));
        challenges.findById(RESCUE).orElseThrow().publish(mazeScoring(), RANKING);

        receive.execute(new ReceiveResultCommand(RESCUE, TURN, RESCUE_PANEL));

        Attempt scored = attempts.findById(TURN).orElseThrow();
        assertThat(scored.getLatestSnapshot().rulebookVersion()).isEqualTo(RulebookVersion.first());
        assertThat(scored.getFinalScore()).isEqualTo(130.0);
    }

    private static ScoringScheme mazeScoring() {
        return ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0),
                new ObjectivesRule("Objetivos", 20.0)), List.of(new PenaltyRule("Faltas", 10.0)));
    }

    private static SourceDelivery sensors(double seconds, int objectives, int faults, String judgeId) {
        return new SourceDelivery(new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of()),
                JudgeId.of(judgeId));
    }
}
