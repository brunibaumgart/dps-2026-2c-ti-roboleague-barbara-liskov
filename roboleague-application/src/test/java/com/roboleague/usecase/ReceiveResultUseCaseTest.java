package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RulebookReference;
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
import com.roboleague.evaluation.scheme.BestAttempt;
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
import com.roboleague.scheduling.SlotCancellation;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.support.ActorId;
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
    private static final SlotId SLOT = SlotId.of("slot-1");
    private static final SlotId OTHER_SLOT = SlotId.of("slot-2");
    private static final AttemptId TURN = AttemptId.of(SLOT, 1);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final SourceDelivery RESCUE_PANEL = new SourceDelivery(
            new JudgeScores(Map.of(JudgeId.of("judge-1"), 8.0, JudgeId.of("judge-2"), 6.0), Map.of(RESCUED.name(), 2.0)), JudgeId.of("judge-2"));

    private InMemoryRoundRepository rounds;
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
                new ScoreRules(List.of(new ObjectivesRule("Zonas despejadas", Points.of(15)),
                        new VictimsRule("Víctimas rescatadas", RESCUED, Points.of(25)),
                        new JudgeSubjectiveRule("Panel técnico", 5.0)), List.of(), List.of()),
                new Unlimited()), RANKING));
        rounds = new InMemoryRoundRepository();
        scheduleFor(MAZE);
        receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    }

    private void scheduleFor(ChallengeId challengeId) {
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1", RoundScope.of(challengeId, EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)));
        SlotAssignment assignment = SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Madera"),
                List.of(Judge.of(JudgeId.of("judge-1"), "Juez Uno", "General"), Judge.of(JudgeId.of("judge-2"), "Juez Dos", "General")));
        round = round.addSlot(Slot.of(SlotIdentity.of(SLOT, RoundId.of("r-1"), TeamId.of("t-1")), assignment,
                new TimeWindow(START, START.plusMinutes(10))));
        round = round.addSlot(Slot.of(SlotIdentity.of(OTHER_SLOT, RoundId.of("r-1"), TeamId.of("t-2")), assignment,
                new TimeWindow(START.plusMinutes(15), START.plusMinutes(25))));
        rounds.save(round);
    }

    @Test
    void firstCaptureCannotOpenAnAttemptForAnotherChallenge() {
        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(RESCUE, SLOT, sensors(90, 3, 0, "judge-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Round r-1 belongs to challenge ch-maze, not ch-rescue");
        assertThat(attempts.findById(TURN)).isEmpty();
    }

    @Test
    @DisplayName("Capturar dos veces un turno de un intento no borra el valor original ni abre otro intento")
    void givenACapturedTurnThenASecondCaptureIsRefusedAndTheOriginalStays() {
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1")));

        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(90.0, 0, 3, "judge-2"))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessage("Turn slot-1 already ran 1 attempt(s); rulebook v1 admits 1 intento por turno");
        assertThat(attempts.findAll()).hasSize(1);

        Attempt stored = attempts.findById(TURN).orElseThrow();
        assertThat(stored.getRevisionHistory()).hasSize(1);
        assertThat(stored.getRevisionHistory().getFirst().metrics().timeTakenSeconds()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("El intento sale del turno: su ronda y su equipo son los del slot")
    void givenAResultThenTheAttemptBelongsToTheTurnsRoundAndTeam() {
        Reception reception = receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1")));

        assertThat(reception).isInstanceOfSatisfying(Reception.Received.class, received -> {
            assertThat(received.attempt().getId()).isEqualTo(TURN);
            assertThat(received.attempt().getRoundId()).isEqualTo(RoundId.of("r-1"));
            assertThat(received.attempt().getTeamId()).isEqualTo(TeamId.of("t-1"));
        });
    }

    @Test
    @DisplayName("Un turno que no existe no recibe resultados")
    void givenASlotThatDoesNotExistThenTheResultIsRefused() {
        assertThatThrownBy(() -> receive.execute(
                new ReceiveResultCommand(MAZE, SlotId.of("slot-9"), sensors(55.0, 4, 0, "judge-1"))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Slot not found: slot-9");
    }

    @Test
    @DisplayName("Un juez que no está asignado al turno no carga resultados")
    void givenAJudgeNotAssignedToTheSlotThenTheResultIsRejected() {
        Reception reception = receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-9")));

        assertThat(reception).isEqualTo(new Reception.Rejected(List.of("judge judge-9 is not assigned to slot slot-1")));
        assertThat(attempts.findById(TURN)).isEmpty();
    }

    @Test
    @DisplayName("F3: en el desafío mixto el intento queda pendiente hasta que llega el panel de jueces")
    void givenTheMixedChallengeThenTheAttemptAwaitsTheJudgePanelAndThenScores() {
        scheduleFor(RESCUE);
        receive.execute(new ReceiveResultCommand(RESCUE, SLOT, sensors(90.0, 3, 0, "judge-1")));
        assertThat(attempts.findById(TURN).orElseThrow().getStatus()).isEqualTo(AttemptStatus.AWAITING_SOURCES);

        receive.execute(new ReceiveResultCommand(RESCUE, SLOT, RESCUE_PANEL));

        Attempt scored = attempts.findById(TURN).orElseThrow();
        assertThat(scored.getStatus()).isEqualTo(AttemptStatus.EVALUATED);
        // 3 zones => 45; 2 victims => 50; judges 8 and 6 => average 7 * 5 = 35
        assertThat(scored.getScoreBreakdown().totalScore()).isEqualTo(Points.of(130));
    }

    @Test
    void givenMeasurementsTheRulebookRejectsThenNothingIsSavedAndTheProblemsComeBack() {
        scheduleFor(RESCUE);
        Reception reception = receive.execute(new ReceiveResultCommand(RESCUE, SLOT, new SourceDelivery(
                new JudgeScores(Map.of(JudgeId.of("judge-1"), 8.0), Map.of()), JudgeId.of("judge-1"))));

        assertThat(reception).isEqualTo(new Reception.Rejected(List.of("measurement 'victimas_rescatadas' is missing")));
        assertThat(attempts.findById(TURN)).isEmpty();
    }

    @Test
    void givenAnAttemptOfAnotherChallengeThenItsResultsCannotArriveForThisOne() {
        scheduleFor(RESCUE);
        receive.execute(new ReceiveResultCommand(RESCUE, SLOT, sensors(90.0, 3, 0, "judge-1")));

        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(90.0, 3, 0, "judge-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Round r-1 belongs to challenge ch-rescue, not ch-maze");
        Attempt stored = attempts.findById(TURN).orElseThrow();
        assertThat(stored.getRulebookReference().challengeId()).isEqualTo(RESCUE);
        assertThat(stored.getEventHistory()).hasSize(1);
        assertThat(stored.getStatus()).isEqualTo(AttemptStatus.AWAITING_SOURCES);
    }

    @Test
    @DisplayName("La fuente que llega después de una versión nueva se puntúa con la versión del intento")
    void givenANewRulebookVersionThenAPendingAttemptIsStillScoredWithItsOwn() {
        scheduleFor(RESCUE);
        receive.execute(new ReceiveResultCommand(RESCUE, SLOT, sensors(90.0, 3, 0, "judge-1")));
        publishVersion(RESCUE, RANKING);

        receive.execute(new ReceiveResultCommand(RESCUE, SLOT, RESCUE_PANEL));

        Attempt scored = attempts.findById(TURN).orElseThrow();
        assertThat(scored.getLatestSnapshot().rulebookVersion()).isEqualTo(RulebookVersion.first());
        assertThat(scored.getScoreBreakdown().totalScore()).isEqualTo(Points.of(130));
    }

    @Test
    @DisplayName("Corrección E1: el dominio numera los intentos de un turno en orden de llegada, hasta los que admite")
    void givenSeveralAttemptsPerTurnThenTheDomainNumbersThemInArrivalOrder() {
        publishVersion(MAZE, threeAttemptsPerTurn());

        Reception first = receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 2, 0, "judge-1")));
        Reception second = receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 5, 0, "judge-1")));
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 1, 0, "judge-1")));

        assertThat(List.of(first, second)).extracting(reception -> ((Reception.Received) reception).attempt().getId())
                .containsExactly(AttemptId.of(SLOT, 1), AttemptId.of(SLOT, 2));
        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 5, 0, "judge-1"))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessage("Turn slot-1 already ran 3 attempt(s); rulebook v2 admits el mejor de 3 intentos por turno");
        assertThat(attempts.findAll()).extracting(Attempt::getId).containsExactlyInAnyOrder(
                AttemptId.of(SLOT, 1), AttemptId.of(SLOT, 2), AttemptId.of(SLOT, 3));
    }

    @Test
    @DisplayName("Un turno se consulta con sus intentos en orden, el reglamento de su categoría y si admite otro resultado")
    void givenATurnWithAttemptsThenTheyAreListedInTheOrderTheyWereOpened() {
        publishVersion(MAZE, threeAttemptsPerTurn());
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 2, 0, "judge-1")));
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 5, 0, "judge-1")));
        QueryTurnAttemptsUseCase query = new QueryTurnAttemptsUseCase(rounds, attempts, challenges);

        TurnResults turn = query.execute(SLOT);
        assertThat(turn.attempts()).extracting(Attempt::getId)
                .containsExactly(AttemptId.of(SLOT, 1), AttemptId.of(SLOT, 2));
        assertThat(turn.rulebook().version()).isEqualTo(new RulebookVersion(2));
        assertThat(turn.acceptsResult()).isTrue();
        assertThat(query.execute(OTHER_SLOT).attempts()).isEmpty();
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 1, 0, "judge-1")));
        assertThat(query.execute(SLOT).acceptsResult()).isFalse();
        assertThatThrownBy(() -> query.execute(SlotId.of("slot-9"))).isInstanceOf(NotFoundException.class)
                .hasMessage("Slot not found: slot-9");
    }

    @Test
    @DisplayName("Un turno cancelado no admite resultados, aunque su reglamento admita otro intento")
    void givenACancelledTurnThenItDoesNotAcceptResults() {
        Round round = rounds.findById(RoundId.of("r-1")).orElseThrow();
        rounds.save(round.cancelSlot(OTHER_SLOT, new SlotCancellation(ActorId.of("org-1"), "No se presentó", START)));

        TurnResults cancelled = new QueryTurnAttemptsUseCase(rounds, attempts, challenges).execute(OTHER_SLOT);

        assertThat(cancelled.attempts()).isEmpty();
        assertThat(cancelled.acceptsResult()).isFalse();
    }

    @Test
    @DisplayName("Intentos guardados con números salteados son un dato inconsistente, no un conflicto a reintentar")
    void givenStoredAttemptsThatSkipANumberThenTheTurnIsInconsistent() {
        Challenge maze = challenges.findById(MAZE).orElseThrow();
        attempts.save(Attempt.of(AttemptIdentity.of(AttemptId.of(SLOT, 2), RoundId.of("r-1"), TeamId.of("t-1")),
                RulebookReference.of(MAZE, maze.currentRulebook())));

        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1"))))
                .isInstanceOf(InconsistentStateException.class)
                .hasMessage("the attempts of turn slot-1 skip number 1");
        assertThatThrownBy(() -> new QueryTurnAttemptsUseCase(rounds, attempts, challenges).execute(SLOT))
                .isInstanceOf(InconsistentStateException.class);
    }

    @Test
    @DisplayName("F1: una categoría con resultados sigue con su versión aunque el desafío publique otra")
    void givenACategoryWithResultsThenANewTurnOpensWithTheVersionThatScoredThem() {
        receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1")));
        publishVersion(MAZE, threeAttemptsPerTurn());

        Reception other = receive.execute(new ReceiveResultCommand(MAZE, OTHER_SLOT, sensors(55.0, 4, 0, "judge-1")));

        assertThat(other).isInstanceOfSatisfying(Reception.Received.class, received ->
                assertThat(received.attempt().getRulebookReference().version()).isEqualTo(RulebookVersion.first()));
        assertThatThrownBy(() -> receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1"))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageEndingWith("rulebook v1 admits 1 intento por turno");
    }

    @Test
    @DisplayName("F1: una categoría que todavía no jugó usa la versión vigente")
    void givenACategoryWithoutResultsThenItsFirstAttemptOpensWithTheCurrentVersion() {
        publishVersion(MAZE, threeAttemptsPerTurn());

        Reception reception = receive.execute(new ReceiveResultCommand(MAZE, SLOT, sensors(55.0, 4, 0, "judge-1")));

        assertThat(reception).isInstanceOfSatisfying(Reception.Received.class, received ->
                assertThat(received.attempt().getRulebookReference().version()).isEqualTo(new RulebookVersion(2)));
    }

    /**
     * The repository hands out copies, so a new version only counts once the challenge is saved again.
     */
    private void publishVersion(ChallengeId challengeId, RankingScheme ranking) {
        Challenge challenge = challenges.findById(challengeId).orElseThrow();
        challenge.publish(mazeScoring(), ranking);
        challenges.save(challenge);
    }

    private static RankingScheme threeAttemptsPerTurn() {
        return new RankingScheme(new AllRounds(), new BestAttempt(3), List.of(new HigherTotal()));
    }

    private static ScoringScheme mazeScoring() {
        return ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", Points.of(100), 60.0, 1.0, 2.0, Points.ZERO),
                new ObjectivesRule("Objetivos", Points.of(20))), List.of(new PenaltyRule("Faltas", Points.of(10))));
    }

    private static SourceDelivery sensors(double seconds, int objectives, int faults, String judgeId) {
        return new SourceDelivery(new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of()),
                JudgeId.of(judgeId));
    }
}
