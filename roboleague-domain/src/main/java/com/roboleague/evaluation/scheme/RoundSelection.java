package com.roboleague.evaluation.scheme;

import java.util.List;

/**
 * Strategy that decides which of a team's rounds count for its challenge score.
 */
public interface RoundSelection {

    ChallengeScore select(List<RoundScore> rounds);

    String describe();
}
