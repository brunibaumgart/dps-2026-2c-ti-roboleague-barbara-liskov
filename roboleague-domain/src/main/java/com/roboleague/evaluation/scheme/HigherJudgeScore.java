package com.roboleague.evaluation.scheme;

public final class HigherJudgeScore implements TieBreakCriterion {

    @Override
    public String name() {
        return "Mayor nota de jueces";
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return MissingGoesLast.higherFirst(a.judgeScore(), b.judgeScore());
    }
}
