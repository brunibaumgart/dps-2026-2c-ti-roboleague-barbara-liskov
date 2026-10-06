package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.rules.ScoreRule;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The rule types a rulebook accepts in one of its lists: base rules, bonuses or deductions. Registering a type
 * only compiles if its rule belongs to the section, so the catalog cannot put a penalty among the bonuses.
 */
final class RuleSection<R extends ScoreRule> {
    private final String name;
    private final Map<String, Function<RuleDefinition, R>> builders = new HashMap<>();

    RuleSection(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    void register(String type, Function<RuleDefinition, R> builder) {
        builders.put(type, builder);
    }

    boolean accepts(String type) {
        return builders.containsKey(type);
    }

    Optional<Function<RuleDefinition, R>> builderOf(String type) {
        return Optional.ofNullable(builders.get(type));
    }
}
