package com.roboleague.evaluation.rules;

/**
 * A rule that scores what the robot achieved on the track. It never subtracts: what is deducted is a
 * {@link DeductionRule}.
 */
public interface BaseRule extends ScoreRule {
}
