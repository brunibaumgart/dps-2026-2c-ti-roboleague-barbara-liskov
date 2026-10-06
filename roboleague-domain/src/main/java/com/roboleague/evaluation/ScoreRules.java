package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The rules a scoring scheme adds up: its score rules and, apart, its bonus rules, whose sum the bonus limit caps.
 */
public record ScoreRules(List<ScoreRule> rules, List<ScoreRule> bonuses) {
    public ScoreRules {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
        bonuses = List.copyOf(Objects.requireNonNull(bonuses, "bonuses cannot be null"));
        if (rules.isEmpty() && bonuses.isEmpty()) {
            throw new IllegalArgumentException("a scoring scheme needs at least one rule");
        }
    }

    public List<ScoreRule> all() {
        List<ScoreRule> all = new ArrayList<>(rules);
        all.addAll(bonuses);
        return List.copyOf(all);
    }
}
