package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object consolidating all performance metrics of a team for ranking and tie-breaking.
 */
public record PerformanceSummary(ScoreMetrics scoreMetrics, SecondaryMetrics secondaryMetrics) {
    public PerformanceSummary {
        Objects.requireNonNull(scoreMetrics, "scoreMetrics cannot be null");
        Objects.requireNonNull(secondaryMetrics, "secondaryMetrics cannot be null");
    }

    public double totalScore() {
        return scoreMetrics.totalScore();
    }

    public double bestAttemptTime() {
        return scoreMetrics.bestAttemptTime();
    }

    public int totalPenalties() {
        return secondaryMetrics.totalPenalties();
    }

    public double judgeSubjectiveScore() {
        return secondaryMetrics.judgeSubjectiveScore();
    }

    public boolean isTiedWith(PerformanceSummary other) {
        if (other == null) return false;
        return Double.compare(totalScore(), other.totalScore()) == 0
                && Double.compare(bestAttemptTime(), other.bestAttemptTime()) == 0
                && totalPenalties() == other.totalPenalties()
                && Double.compare(judgeSubjectiveScore(), other.judgeSubjectiveScore()) == 0;
    }

    public static PerformanceSummary of(ScoreMetrics scoreMetrics, SecondaryMetrics secondaryMetrics) {
        return new PerformanceSummary(scoreMetrics, secondaryMetrics);
    }

    public static PerformanceSummary of(double totalScore, double bestAttemptTime, int totalPenalties, double judgeScore) {
        return new PerformanceSummary(
                new ScoreMetrics(totalScore, bestAttemptTime),
                new SecondaryMetrics(totalPenalties, judgeScore)
        );
    }
}
