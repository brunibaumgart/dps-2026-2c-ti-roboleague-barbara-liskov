package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.CappedAt;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceContribution;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.CLOCK;
import static com.roboleague.support.TestValues.TIME;
import static com.roboleague.support.TestValues.ids;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * F2 through the use case: the breakdown of an attempt scored with a capped rulebook shows the cut as one more item,
 * and what each source contributed before it.
 */
class GetAttemptBreakdownUseCaseTest {
    private static final JudgeId JUDGE = JudgeId.of("j-1");
    private static final ChallengeId CHALLENGE = ChallengeId.of("ch-capped");
    private static final AttemptId ATTEMPT = AttemptId.of(SlotId.of("slot-1"), 1);
    private static final Metric ZONE = Metric.sensor("zona_alcanzada");
    private static final Metric DISTANCE = Metric.sensor("distancia_metros");

    private final InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
    private final InMemoryRoundRepository rounds = new InMemoryRoundRepository();
    private final InMemoryAttemptRepository attempts = new InMemoryAttemptRepository();
    private final ReceiveResultUseCase receive = new ReceiveResultUseCase(attempts, rounds, challenges, CLOCK, ids());
    private final GetAttemptBreakdownUseCase breakdown = new GetAttemptBreakdownUseCase(attempts, challenges);

    GetAttemptBreakdownUseCaseTest() {
        MetricSheet milestones = new MetricSheet(List.of(
                new MetricDefinition(ZONE, MeasurementUnit.COUNT, ValueRange.between(0.0, 1.0)),
                new MetricDefinition(DISTANCE, MeasurementUnit.METERS, ValueRange.atLeast(0.0))));
        challenges.save(Challenge.draft(CHALLENGE, EditionId.of("ed-1"), "Laberinto").publish(new ScoringScheme(milestones,
                        new ScoreRules(List.of(TimeBasedRule.standard(Points.of(100), 60.0)),
                                List.of(new MilestoneBonusRule("Bonus por zona", new Milestone(ZONE, 1.0), Points.of(30)),
                                        new MilestoneBonusRule("Bonus por distancia", new Milestone(DISTANCE, 10.0), Points.of(25))),
                                List.of()),
                        new CappedAt(Points.of(40))),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        RoundId roundId = RoundId.of("r-1");
        rounds.save(Round.of(RoundInfo.of(roundId, "Ronda 1",
                        RoundScope.of(CHALLENGE, EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)))
                .addSlot(Slot.of(SlotIdentity.of(SlotId.of("slot-1"), roundId, TeamId.of("team-a")),
                        SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista", "Madera"),
                                List.of(Judge.of(JUDGE, "Juez", "General"))),
                        new TimeWindow(TIME, TIME.plusMinutes(5)))));
    }

    @Test
    void givenACappedRulebookThenTheBreakdownShowsTheCutAsAnItemWithItsFormula() {
        receive.execute(new ReceiveResultCommand(CHALLENGE, ATTEMPT.slotId(), new SourceDelivery(new Measurements(
                new TrackPerformance(60.0, 0, 0), 0.0, Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0)), JUDGE)));

        AttemptBreakdown explained = breakdown.execute(ATTEMPT);

        assertThat(explained.awaitedSources()).isEmpty();
        assertThat(explained.attempt().getScoreBreakdown().items())
                .extracting(ScoreItem::concept, ScoreItem::rawMetric, ScoreItem::appliedFormula, ScoreItem::subtotal)
                .contains(tuple("Tope de bonificaciones", "bonificaciones obtenidas 55.0 pts",
                        "tope 40.0 pts, recorte -15.0 pts", Points.of(-15)));
        assertThat(explained.attempt().getScoreBreakdown().totalScore()).isEqualTo(Points.of(140));
        assertThat(explained.bySource()).extracting(SourceContribution::source, SourceContribution::subtotal)
                .containsExactly(tuple(ResultSource.AUTOMATIC_MEASUREMENTS, Points.of(155)));
    }

    @Test
    void givenAnUnknownAttemptThenItIsNotFound() {
        assertThatThrownBy(() -> breakdown.execute(ATTEMPT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Attempt not found: slot-1-1");
    }

    @Test
    void givenAnAttemptWhoseRulebookIsGoneThenTheStoredStateIsInconsistent() {
        receive.execute(new ReceiveResultCommand(CHALLENGE, ATTEMPT.slotId(), new SourceDelivery(new Measurements(
                new TrackPerformance(60.0, 0, 0), 0.0, Map.of(ZONE.name(), 0.0, DISTANCE.name(), 0.0)), JUDGE)));
        InMemoryChallengeRepository empty = new InMemoryChallengeRepository();

        assertThatThrownBy(() -> new GetAttemptBreakdownUseCase(attempts, empty).execute(ATTEMPT))
                .isInstanceOf(InconsistentStateException.class)
                .hasMessage("Rulebook ch-capped v1 not found");
    }

    @Test
    void givenAttemptsScoredWithDifferentVersionsThenEachIsExplainedWithItsOwnRulebook() {
        RoundId second = RoundId.of("r-2");
        rounds.save(Round.of(RoundInfo.of(second, "Ronda 2",
                        RoundScope.of(CHALLENGE, EditionId.of("ed-1"), CategoryId.of("cat-2"), 1)))
                .addSlot(Slot.of(SlotIdentity.of(SlotId.of("slot-2"), second, TeamId.of("team-b")),
                        SlotAssignment.of(Track.active(TrackId.of("trk-2"), "Pista", "Madera"),
                                List.of(Judge.of(JUDGE, "Juez", "General"))),
                        new TimeWindow(TIME, TIME.plusMinutes(5)))));
        Map<String, Double> sensors = Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0);
        receive.execute(new ReceiveResultCommand(CHALLENGE, ATTEMPT.slotId(), new SourceDelivery(
                new Measurements(new TrackPerformance(60.0, 0, 0), 0.0, sensors), JUDGE)));
        Challenge stored = challenges.findById(CHALLENGE).orElseThrow();
        MetricSheet sheet = new MetricSheet(List.of(
                new MetricDefinition(ZONE, MeasurementUnit.COUNT, ValueRange.between(0.0, 1.0)),
                new MetricDefinition(DISTANCE, MeasurementUnit.METERS, ValueRange.atLeast(0.0))));
        stored.publish(new ScoringScheme(sheet, new ScoreRules(List.of(TimeBasedRule.standard(Points.of(50), 60.0)),
                List.of(), List.of()), new CappedAt(Points.of(40))),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        challenges.save(stored);
        receive.execute(new ReceiveResultCommand(CHALLENGE, SlotId.of("slot-2"), new SourceDelivery(
                new Measurements(new TrackPerformance(60.0, 0, 0), 0.0, sensors), JUDGE)));
        AttemptId other = AttemptId.of(SlotId.of("slot-2"), 1);

        Map<AttemptId, AttemptBreakdown> explained = breakdown.byIds(List.of(other, ATTEMPT, other));

        assertThat(explained).containsOnlyKeys(ATTEMPT, other);
        assertThat(explained.get(ATTEMPT).attempt().getScoreBreakdown().totalScore()).isEqualTo(Points.of(140));
        assertThat(explained.get(other).attempt().getScoreBreakdown().totalScore()).isEqualTo(Points.of(50));
        assertThat(explained.get(ATTEMPT).attempt().getRulebookReference().version())
                .isNotEqualTo(explained.get(other).attempt().getRulebookReference().version());
    }

    @Test
    void givenAMissingAttemptThenTheBatchIsInconsistent() {
        assertThatThrownBy(() -> breakdown.byIds(List.of(ATTEMPT)))
                .isInstanceOf(InconsistentStateException.class).hasMessageContaining("slot-1-1");
    }

    @Test
    void givenAnAttemptWhoseChallengeIsGoneThenTheBatchIsInconsistent() {
        receive.execute(new ReceiveResultCommand(CHALLENGE, ATTEMPT.slotId(), new SourceDelivery(new Measurements(
                new TrackPerformance(60.0, 0, 0), 0.0, Map.of(ZONE.name(), 0.0, DISTANCE.name(), 0.0)), JUDGE)));

        assertThatThrownBy(() -> new GetAttemptBreakdownUseCase(attempts, new InMemoryChallengeRepository())
                .byIds(List.of(ATTEMPT))).isInstanceOf(InconsistentStateException.class)
                .hasMessage("Challenge of an attempt not found: [ch-capped]");
    }

    @Test
    void givenAnAttemptScoredWithAVersionItsChallengeNoLongerHasThenTheBatchIsInconsistent() {
        Challenge stored = challenges.findById(CHALLENGE).orElseThrow();
        stored.publish(ScoringScheme.withoutBonuses(List.of(TimeBasedRule.standard(Points.of(50), 60.0)), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        challenges.save(stored);
        receive.execute(new ReceiveResultCommand(CHALLENGE, ATTEMPT.slotId(), new SourceDelivery(new Measurements(
                new TrackPerformance(60.0, 0, 0), 0.0, Map.of()), JUDGE)));
        // The same challenge as stored before v2 was published: the attempt's version is gone.
        InMemoryChallengeRepository onlyFirstVersion = new InMemoryChallengeRepository();
        onlyFirstVersion.save(Challenge.restore(Challenge.draft(CHALLENGE, EditionId.of("ed-1"), "Laberinto"),
                List.of(stored.rulebooks().getFirst())));

        assertThatThrownBy(() -> new GetAttemptBreakdownUseCase(attempts, onlyFirstVersion).byIds(List.of(ATTEMPT)))
                .isInstanceOf(InconsistentStateException.class)
                .hasMessage("Rulebook ch-capped v2 not found");
    }

    @Test
    void givenNoIdsThenTheBatchIsEmpty() {
        assertThat(breakdown.byIds(List.of())).isEmpty();
    }
}
