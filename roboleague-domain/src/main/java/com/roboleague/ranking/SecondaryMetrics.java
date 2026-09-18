package com.roboleague.ranking;

/**
 * Value object representing tie-breaking metrics (penalties and judge rating).
 */
public record SecondaryMetrics(int totalPenalties, double judgeSubjectiveScore) {
    public SecondaryMetrics {
        if (totalPenalties < 0) {
            throw new IllegalArgumentException("totalPenalties cannot be negative");
        }
        if (judgeSubjectiveScore < 0.0) {
            throw new IllegalArgumentException("judgeSubjectiveScore cannot be negative");
        }
    }

    public static SecondaryMetrics of(int totalPenalties, double judgeSubjectiveScore) {
        return new SecondaryMetrics(totalPenalties, judgeSubjectiveScore);
    }
}
