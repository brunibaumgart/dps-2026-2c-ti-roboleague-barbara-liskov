package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

public final class Unlimited implements BonusLimit {

    @Override
    public RuleEvaluation limit(double obtainedBonuses) {
        return RuleEvaluation.empty();
    }
}
