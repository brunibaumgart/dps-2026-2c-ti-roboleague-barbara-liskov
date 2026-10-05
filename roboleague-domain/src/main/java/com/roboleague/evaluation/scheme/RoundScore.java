package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.audit.EvaluationSnapshot;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Score a team got in one round of a challenge: the current evaluation of its attempt in that round.
 */
public record RoundScore(String roundId, EvaluationSnapshot evaluation) {
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

    public int penalties() {
        return evaluation.metrics().penaltiesCount();
    }

    public OptionalDouble judgeScore() {
        if (evaluation.metrics().judgeSubjectiveScores().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(evaluation.metrics().getAverageJudgeScore());
    }
}
