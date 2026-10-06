package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.MetricDeclaration;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.AbandonedVictimsRule;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.BaseRule;
import com.roboleague.evaluation.rules.BonusRule;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.DeductionRule;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.FewerPenalties;
import com.roboleague.evaluation.scheme.HigherJudgeScore;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerTime;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.evaluation.scheme.RoundSelection;
import com.roboleague.evaluation.scheme.TieBreakCriterion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one place where a type named in a definition becomes a class: rules, round selections, bonus limits and
 * tie-break criteria. Both the API and the database rebuild rulebooks through it. A new kind is one more entry,
 * and a new rule type goes in the section of its kind (base rule, bonus or deduction).
 */
public final class RuleCatalog {
    private final RuleSection<BaseRule> base = new RuleSection<>("base rule");
    private final RuleSection<BonusRule> bonuses = new RuleSection<>("bonus");
    private final RuleSection<DeductionRule> deductions = new RuleSection<>("deduction");
    private final Map<String, Function<StrategyDefinition, RoundSelection>> selections = new HashMap<>();
    private final Map<String, Function<StrategyDefinition, BonusLimit>> limits = new HashMap<>();
    private final Map<String, TieBreakCriterion> criteria = new HashMap<>();

    private RuleCatalog() {
    }

    public static RuleCatalog standard() {
        RuleCatalog catalog = new RuleCatalog();
        catalog.base.register(TimeBasedRule.TYPE, TimeBasedRule::from);
        catalog.base.register(ObjectivesRule.TYPE, ObjectivesRule::from);
        catalog.base.register(JudgeSubjectiveRule.TYPE, JudgeSubjectiveRule::from);
        catalog.base.register(PrecisionRule.TYPE, PrecisionRule::from);
        catalog.base.register(VictimsRule.TYPE, VictimsRule::from);
        catalog.base.register(CompositeScoreRule.TYPE,
                definition -> CompositeScoreRule.from(definition, child -> catalog.child(catalog.base, child)));
        catalog.bonuses.register(MilestoneBonusRule.TYPE, MilestoneBonusRule::from);
        catalog.bonuses.register(AllObjectivesBonusRule.TYPE, AllObjectivesBonusRule::from);
        catalog.deductions.register(PenaltyRule.TYPE, PenaltyRule::from);
        catalog.deductions.register(CountedFaultRule.TYPE, CountedFaultRule::from);
        catalog.deductions.register(ResourceConsumptionRule.TYPE, ResourceConsumptionRule::from);
        catalog.deductions.register(AbandonedVictimsRule.TYPE, AbandonedVictimsRule::from);
        catalog.selections.put(BestNOfM.TYPE, BestNOfM::from);
        catalog.selections.put(AllRounds.TYPE, definition -> new AllRounds());
        catalog.limits.put(CappedAt.TYPE, CappedAt::from);
        catalog.limits.put(Unlimited.TYPE, definition -> new Unlimited());
        for (TieBreakCriterion criterion : List.of(new HigherTotal(), new LowerTime(), new FewerPenalties(),
                new HigherJudgeScore())) {
            catalog.criteria.put(criterion.code(), criterion);
        }
        return catalog;
    }

    /**
     * Builds both schemes of a rulebook, or collects every problem found (invalid metric declarations, unknown
     * types, missing or invalid parameters) so whoever sent the definition can fix them all at once.
     */
    public RulebookAssembly assemble(RulebookDefinition definition) {
        List<String> problems = new ArrayList<>();
        List<MetricDefinition> declared = new ArrayList<>();
        for (MetricDeclaration declaration : definition.metrics()) {
            build("metric '" + declaration.metric().name() + "'", () -> metric(declaration), problems)
                    .ifPresent(declared::add);
        }
        Optional<MetricSheet> metrics = build("metrics", () -> new MetricSheet(declared), problems);
        List<BaseRule> baseRules = rulesOf(base, definition.scoring().rules(), problems);
        List<BonusRule> bonusRules = rulesOf(bonuses, definition.scoring().bonuses().rules(), problems);
        List<DeductionRule> deductionRules = rulesOf(deductions, definition.scoring().deductions(), problems);
        Optional<BonusLimit> limit = build("bonus limit",
                () -> resolve(limits, definition.scoring().bonuses().limit(), BonusLimit::definition), problems);
        Optional<RoundSelection> selection = build("round selection",
                () -> resolve(selections, definition.ranking().roundSelection(), RoundSelection::definition), problems);
        List<TieBreakCriterion> chain = new ArrayList<>();
        for (String code : definition.ranking().criteria()) {
            TieBreakCriterion criterion = criteria.get(code);
            if (criterion == null) {
                problems.add("tie-break criterion '" + code + "': unknown criterion");
            } else {
                chain.add(criterion);
            }
        }
        if (!problems.isEmpty()) {
            return new RulebookAssembly.Rejected(problems);
        }
        Optional<ScoringScheme> scoring = build("scoring", () -> new ScoringScheme(metrics.orElseThrow(),
                new ScoreRules(baseRules, bonusRules, deductionRules), limit.orElseThrow()), problems);
        Optional<RankingScheme> ranking = build("ranking",
                () -> new RankingScheme(selection.orElseThrow(), chain), problems);
        if (!problems.isEmpty()) {
            return new RulebookAssembly.Rejected(problems);
        }
        return new RulebookAssembly.Assembled(scoring.orElseThrow(), ranking.orElseThrow());
    }

    private <R extends ScoreRule> R rule(RuleSection<R> section, RuleDefinition definition) {
        Function<RuleDefinition, R> builder = section.builderOf(definition.type())
                .orElseThrow(() -> new IllegalArgumentException(misplaced(section, definition.type())));
        R rule = builder.apply(definition);
        rejectUnknown(definition.arguments().unknownTo(rule.definition().arguments()));
        return rule;
    }

    /**
     * Why a type is not accepted in a section: it belongs to another list, or no list knows it.
     */
    private String misplaced(RuleSection<?> section, String type) {
        return List.of(base, bonuses, deductions).stream()
                .filter(other -> other.accepts(type))
                .findFirst()
                .map(other -> "type '" + type + "' is a " + other.name() + ", not a " + section.name())
                .orElse("unknown rule type '" + type + "'");
    }

    private static MetricDefinition metric(MetricDeclaration declaration) {
        MetricDefinition metric = MetricDefinition.from(declaration);
        rejectUnknown(unknownParameters(declaration.range().unknownTo(metric.declaration().range())));
        return metric;
    }

    private <R extends ScoreRule> R child(RuleSection<R> section, RuleDefinition definition) {
        try {
            return rule(section, definition);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("rule '" + definition.name() + "': " + invalid.getMessage());
        }
    }

    private <R extends ScoreRule> List<R> rulesOf(RuleSection<R> section, List<RuleDefinition> definitions,
                                                 List<String> problems) {
        List<R> built = new ArrayList<>();
        for (RuleDefinition definition : definitions) {
            build("rule '" + definition.name() + "'", () -> rule(section, definition), problems).ifPresent(built::add);
        }
        return built;
    }

    private static <T> T resolve(Map<String, Function<StrategyDefinition, T>> registry, StrategyDefinition definition,
                                 Function<T, StrategyDefinition> describe) {
        Function<StrategyDefinition, T> builder = registry.get(definition.type());
        if (builder == null) {
            throw new IllegalArgumentException("unknown type '" + definition.type() + "'");
        }
        T built = builder.apply(definition);
        rejectUnknown(unknownParameters(definition.numbers().unknownTo(describe.apply(built).numbers())));
        return built;
    }

    private static List<String> unknownParameters(List<String> names) {
        return names.stream().map(name -> "unknown parameter '" + name + "'").toList();
    }

    /**
     * A definition that carries something its piece does not take is rejected: a misspelled parameter would
     * otherwise be ignored and the piece would score with what it did understand.
     */
    private static void rejectUnknown(List<String> unknown) {
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", unknown));
        }
    }

    /**
     * Rule constructors reject invalid parameters with {@link IllegalArgumentException}: a broken invariant for
     * code, an expected failure for a definition that came from outside. This is where one becomes the other.
     */
    private static <T> Optional<T> build(String what, Supplier<T> builder, List<String> problems) {
        try {
            return Optional.of(builder.get());
        } catch (IllegalArgumentException invalid) {
            problems.add(what + ": " + invalid.getMessage());
            return Optional.empty();
        }
    }
}
