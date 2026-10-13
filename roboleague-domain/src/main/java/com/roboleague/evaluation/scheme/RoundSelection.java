package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.List;

/**
 * Strategy that decides which of a team's rounds count for its challenge score.
 */
public interface RoundSelection {

    ChallengeScore select(List<RoundScore> rounds);

    /**
     * Whether a round with this (positive) number may be scheduled under this selection. Checked before
     * scheduling so a team never ends up with more rounds than the selection can score.
     */
    default boolean allowsRound(int roundNumber) {
        return true;
    }

    String describe();

    StrategyDefinition definition();
}
