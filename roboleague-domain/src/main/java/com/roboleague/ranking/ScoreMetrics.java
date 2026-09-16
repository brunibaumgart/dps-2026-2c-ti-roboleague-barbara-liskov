package com.roboleague.ranking;

/**
 * Value object representing primary ranking metrics (score and time).
 */
public record ScoreMetrics(double totalScore, double bestAttemptTime) {
    public static ScoreMetrics of(double totalScore, double bestAttemptTime) {
        return new ScoreMetrics(totalScore, bestAttemptTime);
    }
}
