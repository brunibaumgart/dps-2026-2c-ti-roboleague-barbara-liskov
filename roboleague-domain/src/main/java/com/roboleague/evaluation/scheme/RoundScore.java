package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.audit.EvaluationSnapshot;
import com.roboleague.scheduling.RoundId;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Score a team got in one round of a challenge: the current evaluation of its attempt in that round.
 */
public record RoundScore(RoundId roundId, EvaluationSnapshot evaluation) {
    public RoundScore {
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
    }

    public double total() {
        return evaluation.breakdown().totalScore();
    }

    public double timeTakenSeconds() {
        return evaluation.metrics().timeTakenSeconds();
    }

    public double deducted() {
        return evaluation.breakdown().deducted();
    }

    public OptionalDouble judgeScore() {
        if (evaluation.metrics().judgeSubjectiveScores().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(evaluation.metrics().getAverageJudgeScore());
    }
}
