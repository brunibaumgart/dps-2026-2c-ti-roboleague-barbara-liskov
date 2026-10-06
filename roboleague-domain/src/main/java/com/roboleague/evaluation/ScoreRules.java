package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.BaseRule;
import com.roboleague.evaluation.rules.BonusRule;
import com.roboleague.evaluation.rules.DeductionRule;
import com.roboleague.evaluation.rules.ScoreRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The rules a scoring scheme adds up, each in its section: base rules, bonus rules, whose sum the bonus limit caps,
 * and deductions, which the limit never touches. The types keep a penalty out of the bonuses.
 */
public record ScoreRules(List<BaseRule> base, List<BonusRule> bonuses, List<DeductionRule> deductions) {
    public ScoreRules {
        base = List.copyOf(Objects.requireNonNull(base, "base cannot be null"));
        bonuses = List.copyOf(Objects.requireNonNull(bonuses, "bonuses cannot be null"));
        deductions = List.copyOf(Objects.requireNonNull(deductions, "deductions cannot be null"));
        if (base.isEmpty() && bonuses.isEmpty() && deductions.isEmpty()) {
            throw new IllegalArgumentException("a scoring scheme needs at least one rule");
        }
    }

    public List<ScoreRule> all() {
        List<ScoreRule> all = new ArrayList<>(base);
        all.addAll(bonuses);
        all.addAll(deductions);
        return List.copyOf(all);
    }
}
