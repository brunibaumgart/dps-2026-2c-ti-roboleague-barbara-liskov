package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

public final class Unlimited implements BonusLimit {

    public static final String TYPE = "unlimited";

    @Override
    public StrategyDefinition definition() {
        return new StrategyDefinition(TYPE, Parameters.none());
    }

    @Override
    public RuleEvaluation limit(double obtainedBonuses) {
        return RuleEvaluation.empty();
    }
}
