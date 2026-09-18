package com.roboleague.ranking;

/**
 * Value object representing primary ranking metrics (score and time).
 */
public record ScoreMetrics(double totalScore, double bestAttemptTime) {
    public ScoreMetrics {
        if (totalScore < 0.0) {
            throw new IllegalArgumentException("totalScore cannot be negative");
        }
        if (Double.isInfinite(bestAttemptTime) || bestAttemptTime < 0.0) {
            throw new IllegalArgumentException("bestAttemptTime must be a finite non-negative value");
        }
    }

    public static ScoreMetrics of(double totalScore, double bestAttemptTime) {
        return new ScoreMetrics(totalScore, bestAttemptTime);
    }
}
