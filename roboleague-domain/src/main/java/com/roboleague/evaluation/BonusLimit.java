package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

/**
 * Strategy for how much of the bonuses a rulebook lets count, applied to their sum and not to each one (F2).
 */
public interface BonusLimit {

    RuleEvaluation limit(double obtainedBonuses);

    StrategyDefinition definition();
}
