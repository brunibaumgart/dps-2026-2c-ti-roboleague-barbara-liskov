package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BonusCapTest {

    private static final Metric ZONE = Metric.sensor("zona_alcanzada");
    private static final Metric DISTANCE = Metric.sensor("distancia_metros");
    private static final ScoreRule ZONE_BONUS = new MilestoneBonusRule("Bonus por zona", new Milestone(ZONE, 1.0), 30.0);
    private static final ScoreRule DISTANCE_BONUS =
            new MilestoneBonusRule("Bonus por distancia", new Milestone(DISTANCE, 10.0), 25.0);
    private static final ScoreRule TIME = TimeBasedRule.standard(100.0, 60.0);
    private static final MetricSheet MILESTONES = new MetricSheet(List.of(
            new MetricDefinition(ZONE, MeasurementUnit.COUNT, ValueRange.between(0.0, 1.0)),
            new MetricDefinition(DISTANCE, MeasurementUnit.METERS, ValueRange.atLeast(0.0))));
    private static final RankingScheme ANY_RANKING = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));

    private final RawMetrics bothMilestonesAt60Seconds = new RawMetrics(new TrackPerformance(60.0, 0, 0),
            EvaluationFeedback.withMeasurements(Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0)));

    private static boolean isBonus(ScoreItem item) {
        return item.concept().equals(ZONE_BONUS.getRuleName()) || item.concept().equals(DISTANCE_BONUS.getRuleName());
    }

    private static Rulebook rulebook(BonusLimit limit) {
        return new Rulebook(RulebookVersion.first(),
                new ScoringScheme(MILESTONES, new ScoreRules(List.of(TIME), List.of(ZONE_BONUS, DISTANCE_BONUS), List.of()), limit), ANY_RANKING);
    }

    private static ScoreItem capItem(ScoreBreakdown breakdown) {
        return breakdown.items().stream()
                .filter(item -> item.concept().equals("Tope de bonificaciones"))
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest(name = "bonificaciones 55 con tope {0} → recorte {1}, total {2}")
    @CsvSource({"40.0, 15.0, 140.0", "55.0, 0.0, 155.0", "100.0, 0.0, 155.0", "0.0, 55.0, 100.0"})
    @DisplayName("El tope recorta solo lo que la suma de bonificaciones supera y no toca las reglas base")
    void givenACapThenOnlyTheExcessOfTheBonusesIsCut(double cap, double expectedCut, double expectedTotal) {
        ScoreBreakdown breakdown = rulebook(new CappedAt(cap)).evaluate(bothMilestonesAt60Seconds);

        assertThat(capItem(breakdown).subtotal()).isEqualTo(-expectedCut);
        assertThat(breakdown.totalScore()).isEqualTo(expectedTotal);
    }

    @Test
    @DisplayName("El tope se aplica sobre el conjunto: dos bonificaciones menores al tope se recortan si juntas lo superan")
    void givenBonusesEachUnderTheCapThenTheirSumIsStillCapped() {
        ScoreBreakdown breakdown = rulebook(new CappedAt(40.0)).evaluate(bothMilestonesAt60Seconds);

        assertThat(breakdown.items()).filteredOn(BonusCapTest::isBonus)
                .extracting(ScoreItem::subtotal)
                .containsExactly(30.0, 25.0);
        assertThat(capItem(breakdown).subtotal()).isEqualTo(-15.0);
    }

    @Test
    @DisplayName("La explicación muestra las bonificaciones obtenidas, el tope aplicado y el recorte")
    void givenACapThenTheBreakdownShowsObtainedCapAndCut() {
        ScoreItem cap = capItem(rulebook(new CappedAt(40.0)).evaluate(bothMilestonesAt60Seconds));

        assertThat(cap.rawMetric()).contains("55.0");
        assertThat(cap.appliedFormula()).contains("tope 40.0").contains("recorte -15.0");
    }

    @Test
    @DisplayName("Agregar el tope no cambia lo que da cada regla de bonificación")
    void givenACapThenEachBonusRuleScoresTheSameAsWithoutIt() {
        ScoreBreakdown capped = rulebook(new CappedAt(40.0)).evaluate(bothMilestonesAt60Seconds);
        ScoreBreakdown uncapped = rulebook(new Unlimited()).evaluate(bothMilestonesAt60Seconds);

        List<ScoreItem> uncappedBonuses = uncapped.items().stream()
                .filter(BonusCapTest::isBonus)
                .toList();
        assertThat(uncappedBonuses).hasSize(2);
        assertThat(capped.items()).filteredOn(BonusCapTest::isBonus)
                .containsExactlyElementsOf(uncappedBonuses);
        assertThat(uncapped.items()).noneMatch(item -> item.concept().equals("Tope de bonificaciones"));
    }

    @Test
    @DisplayName("Sin recorte, el tope aparece igual en la explicación con cero puntos")
    void givenBonusesUnderTheCapThenTheCapItemShowsNoCut() {
        ScoreItem cap = capItem(rulebook(new CappedAt(100.0)).evaluate(bothMilestonesAt60Seconds));

        assertThat(cap.subtotal()).isEqualTo(0.0).isNotNegative();
        assertThat(cap.appliedFormula()).contains("sin recorte");
    }

    @Test
    @DisplayName("El tope se aplica antes del piso en cero: una penalización grande sigue llevando el total a cero")
    void givenACappedBonusAndALargePenaltyThenTheFloorAppliesAfterTheCap() {
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MILESTONES,
                new ScoreRules(List.of(), List.of(ZONE_BONUS, DISTANCE_BONUS), List.of(new PenaltyRule("Faltas", 100.0))),
                new CappedAt(40.0)),
                ANY_RANKING);
        RawMetrics threeFaults = new RawMetrics(new TrackPerformance(60.0, 0, 3),
                EvaluationFeedback.withMeasurements(Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0)));

        ScoreBreakdown breakdown = rulebook.evaluate(threeFaults);

        assertThat(capItem(breakdown).subtotal()).isEqualTo(-15.0);
        assertThat(breakdown.totalScore()).isZero();
        assertThat(breakdown.items()).extracting(ScoreItem::concept).endsWith("Piso en cero");
    }

    @ParameterizedTest(name = "{0} objetivos de 5 → recorte {1}")
    @CsvSource({"4, 15.0", "5, 40.0"})
    @DisplayName("El bono por completar todos los objetivos es una bonificación más: el tope también lo recorta")
    void givenTheAllObjectivesBonusThenTheCapIncludesIt(int objectives, double expectedCut) {
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MILESTONES, new ScoreRules(
                List.of(TIME), List.of(ZONE_BONUS, DISTANCE_BONUS, new AllObjectivesBonusRule("Todos", 5, 25.0)),
                List.of()), new CappedAt(40.0)), ANY_RANKING);
        RawMetrics run = new RawMetrics(new TrackPerformance(60.0, objectives, 0),
                EvaluationFeedback.withMeasurements(Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0)));

        assertThat(capItem(rulebook.evaluate(run)).subtotal()).isEqualTo(-expectedCut);
    }

    @Test
    @DisplayName("Las deducciones no cuentan para el tope: se restan aparte de lo que se recorta")
    void givenDeductionsThenTheCapOnlyLooksAtTheBonuses() {
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MILESTONES,
                new ScoreRules(List.of(TIME), List.of(ZONE_BONUS, DISTANCE_BONUS), List.of(new PenaltyRule("Faltas", 10.0))),
                new CappedAt(40.0)), ANY_RANKING);
        RawMetrics twoFaults = new RawMetrics(new TrackPerformance(60.0, 0, 2),
                EvaluationFeedback.withMeasurements(Map.of(ZONE.name(), 1.0, DISTANCE.name(), 12.0)));

        ScoreBreakdown breakdown = rulebook.evaluate(twoFaults);

        assertThat(capItem(breakdown).subtotal()).isEqualTo(-15.0);
        assertThat(breakdown.totalScore()).isEqualTo(100.0 + 55.0 - 15.0 - 20.0);
    }

    @Test
    @DisplayName("Una regla que resta puesta entre las bonificaciones baja lo obtenido y el tope no recorta")
    void givenARuleThatSubtractsAmongTheBonusesThenTheObtainedSumCanBeNegativeAndNothingIsCut() {
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MILESTONES,
                new ScoreRules(List.of(TIME), List.of(new PenaltyRule("Faltas", 10.0)), List.of()),
                new CappedAt(40.0)), ANY_RANKING);

        ScoreItem cap = capItem(rulebook.evaluate(new RawMetrics(new TrackPerformance(60.0, 0, 2),
                EvaluationFeedback.empty())));

        assertThat(cap.rawMetric()).contains("-20.0");
        assertThat(cap.subtotal()).isZero();
    }

    @Test
    @DisplayName("El recorte del tope no pertenece a ninguna fuente")
    void givenACapThenTheCutIsNotPartOfAnySourceContribution() {
        Rulebook capped = rulebook(new CappedAt(40.0));

        assertThat(capped.contributions(bothMilestonesAt60Seconds))
                .flatExtracting(SourceContribution::items)
                .noneMatch(item -> item.concept().equals("Tope de bonificaciones"));
    }
}
