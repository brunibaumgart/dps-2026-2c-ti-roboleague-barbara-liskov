package com.roboleague.repository.jpa;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookAssembly;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.repository.jpa.ChallengeJpaEntity.MetricJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.RankingJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.RuleJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.RulebookJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.RulebooksJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.ScoringJson;
import com.roboleague.repository.jpa.ChallengeJpaEntity.StrategyJson;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer between the challenges table and the {@link Challenge} aggregate.
 * Rulebooks are stored as their definitions and rebuilt with the same catalog the API uses.
 */
final class ChallengeMapper {
    private final RuleCatalog catalog;

    ChallengeMapper(RuleCatalog catalog) {
        this.catalog = catalog;
    }

    ChallengeJpaEntity toEntity(Challenge challenge) {
        ChallengeJpaEntity entity = new ChallengeJpaEntity();
        entity.id = challenge.getId().value();
        entity.editionId = challenge.getEditionId();
        entity.name = challenge.getName();
        List<RulebookJson> versions = new ArrayList<>();
        for (Rulebook rulebook : challenge.rulebooks()) {
            versions.add(toJson(rulebook));
        }
        entity.rulebooks = new RulebooksJson(versions);
        return entity;
    }

    Challenge toDomain(ChallengeJpaEntity entity) {
        List<Rulebook> rulebooks = new ArrayList<>();
        for (RulebookJson json : entity.rulebooks.versions()) {
            rulebooks.add(toRulebook(entity.id, json));
        }
        return Challenge.restore(Challenge.draft(ChallengeId.of(entity.id), entity.editionId, entity.name), rulebooks);
    }

    private Rulebook toRulebook(String challengeId, RulebookJson json) {
        RulebookDefinition definition = new RulebookDefinition(
                new RulebookDefinition.Scoring(toRules(json.scoring().rules()), toRules(json.scoring().bonuses()),
                        toStrategy(json.scoring().bonusLimit())),
                new RulebookDefinition.Ranking(toStrategy(json.ranking().roundSelection()), json.ranking().criteria()));
        return switch (catalog.assemble(definition)) {
            case RulebookAssembly.Assembled assembled ->
                    new Rulebook(new RulebookVersion(json.version()), assembled.scoring(), assembled.ranking());
            case RulebookAssembly.Rejected rejected -> throw new IllegalStateException(
                    "Stored rulebook v" + json.version() + " of " + challengeId + " cannot be rebuilt: " + rejected.problems());
        };
    }

    private static RulebookJson toJson(Rulebook rulebook) {
        RulebookDefinition definition = rulebook.definition();
        return new RulebookJson(rulebook.version().number(),
                new ScoringJson(toJson(definition.scoring().rules()), toJson(definition.scoring().bonuses()),
                        toJson(definition.scoring().bonusLimit())),
                new RankingJson(toJson(definition.ranking().roundSelection()), definition.ranking().criteria()));
    }

    private static List<RuleJson> toJson(List<RuleDefinition> rules) {
        List<RuleJson> json = new ArrayList<>();
        for (RuleDefinition rule : rules) {
            Map<String, MetricJson> metrics = new HashMap<>();
            rule.arguments().metrics().forEach((role, metric) ->
                    metrics.put(role, new MetricJson(metric.name(), metric.source().name())));
            json.add(new RuleJson(rule.type(), rule.name(), rule.arguments().numbers().values(), metrics,
                    toJson(rule.arguments().rules())));
        }
        return json;
    }

    private static StrategyJson toJson(StrategyDefinition strategy) {
        return new StrategyJson(strategy.type(), strategy.numbers().values());
    }

    private static List<RuleDefinition> toRules(List<RuleJson> json) {
        List<RuleDefinition> rules = new ArrayList<>();
        for (RuleJson rule : json == null ? List.<RuleJson>of() : json) {
            Map<String, Metric> metrics = new HashMap<>();
            if (rule.metrics() != null) {
                rule.metrics().forEach((role, metric) ->
                        metrics.put(role, new Metric(metric.name(), ResultSource.valueOf(metric.source()))));
            }
            rules.add(new RuleDefinition(rule.type(), rule.name(), new RuleArguments(
                    Parameters.of(rule.numbers() == null ? Map.of() : rule.numbers()), metrics, toRules(rule.rules()))));
        }
        return rules;
    }

    private static StrategyDefinition toStrategy(StrategyJson json) {
        return new StrategyDefinition(json.type(), Parameters.of(json.numbers()));
    }
}
