package com.roboleague.api.demo;

import com.roboleague.evaluation.CappedAt;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.rules.AbandonedVictimsRule;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimTariff;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherJudgeScore;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerDeductions;
import com.roboleague.evaluation.scheme.LowerTime;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.List;

/**
 * The three rulebooks of the demo. Between them they use the ten rule types, a composite rule, a penalty,
 * bonuses with a global cap, best N of M rounds and chained tie-break criteria. Each one declares the metrics
 * its rules read.
 */
final class DemoRulebooks {

    static final Metric COLLISIONS = Metric.sensor("colisiones");
    static final Metric CHECKPOINT = Metric.sensor("checkpoint");
    static final Metric LAPS = Metric.sensor("vueltas");
    static final Metric PRECISION = Metric.sensor("precision");
    static final Metric LINE_EXITS = Metric.sensor("salidas_de_linea");
    static final Metric FAST_LAP = Metric.sensor("vuelta_rapida");
    static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    static final Metric FULL_RESCUE = Metric.judged("rescate_completo");

    private DemoRulebooks() {
    }

    private static MetricDefinition count(Metric metric, ValueRange range) {
        return new MetricDefinition(metric, MeasurementUnit.COUNT, range);
    }

    static RulebookDefinition maze() {
        MetricSheet metrics = new MetricSheet(List.of(count(COLLISIONS, ValueRange.atLeast(0.0)),
                count(CHECKPOINT, ValueRange.between(0.0, 1.0)), count(LAPS, ValueRange.atLeast(0.0))));
        ScoreRules rules = new ScoreRules(
                List.of(new CompositeScoreRule("Desempeño en pista", List.of(
                        TimeBasedRule.standard(100.0, 60.0), ObjectivesRule.standard(20.0)))),
                List.of(new MilestoneBonusRule("Checkpoint central", new Milestone(CHECKPOINT, 1.0), 30.0),
                        new MilestoneBonusRule("Vuelta completa", new Milestone(LAPS, 1.0), 20.0),
                        new AllObjectivesBonusRule("Todos los objetivos", 5, 25.0)),
                List.of(new PenaltyRule("Faltas de pista", 15.0),
                        new CountedFaultRule("Colisiones", COLLISIONS, new FaultTariff(1, 5.0)),
                        new ResourceConsumptionRule("Consumo de batería", 80.0, 0.5)));
        RankingScheme ranking = new RankingScheme(new BestNOfM(3, 5),
                List.of(new HigherTotal(), new LowerTime(), new LowerDeductions()));
        return definitionOf(new ScoringScheme(metrics, rules, new CappedAt(40.0)), ranking);
    }

    static RulebookDefinition lineFollower(double bonusCap) {
        MetricSheet metrics = new MetricSheet(List.of(
                new MetricDefinition(PRECISION, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)),
                count(LINE_EXITS, ValueRange.atLeast(0.0)), count(FAST_LAP, ValueRange.between(0.0, 1.0))));
        ScoreRules rules = new ScoreRules(
                List.of(TimeBasedRule.of("Tiempo de vuelta", 100.0, 90.0, 1.0, 1.0, 0.0),
                        new PrecisionRule("Precisión de trazado", PRECISION, 50.0)),
                List.of(new MilestoneBonusRule("Vuelta rápida", new Milestone(FAST_LAP, 1.0), 35.0)),
                List.of(new CountedFaultRule("Salidas de línea", LINE_EXITS, new FaultTariff(2, 10.0))));
        RankingScheme ranking = new RankingScheme(new BestNOfM(2, 3),
                List.of(new HigherTotal(), new LowerDeductions(), new LowerTime()));
        return definitionOf(new ScoringScheme(metrics, rules, new CappedAt(bonusCap)), ranking);
    }

    static RulebookDefinition rescue() {
        MetricSheet metrics = new MetricSheet(List.of(
                count(RESCUED, ValueRange.between(0.0, 4.0)), count(FULL_RESCUE, ValueRange.between(0.0, 1.0))));
        ScoreRules rules = new ScoreRules(
                List.of(new ObjectivesRule("Zonas despejadas", 15.0),
                        new VictimsRule("Víctimas rescatadas", RESCUED, 25.0),
                        new JudgeSubjectiveRule("Panel técnico", 5.0)),
                List.of(new MilestoneBonusRule("Rescate completo", new Milestone(FULL_RESCUE, 1.0), 40.0),
                        new AllObjectivesBonusRule("Todas las zonas despejadas", 4, 20.0)),
                List.of(new AbandonedVictimsRule("Víctimas abandonadas", RESCUED, new VictimTariff(4, 10.0))));
        RankingScheme ranking = new RankingScheme(new BestNOfM(2, 3),
                List.of(new HigherTotal(), new HigherJudgeScore(), new LowerDeductions(), new LowerTime()));
        return definitionOf(new ScoringScheme(metrics, rules, new CappedAt(30.0)), ranking);
    }

    private static RulebookDefinition definitionOf(ScoringScheme scoring, RankingScheme ranking) {
        return new RulebookDefinition(scoring.metrics().declarations(), scoring.definition(), ranking.definition());
    }
}
