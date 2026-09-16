package com.roboleague.ranking;

/**
 * Value object representing tie-breaking metrics (penalties and judge rating).
 */
public record SecondaryMetrics(int totalPenalties, double judgeSubjectiveScore) {
    public static SecondaryMetrics of(int totalPenalties, double judgeSubjectiveScore) {
        return new SecondaryMetrics(totalPenalties, judgeSubjectiveScore);
    }
}
