package com.roboleague.evaluation;

/**
 * Value object representing direct track measurements during an attempt.
 */
public record TrackPerformance(
        double timeTakenSeconds,
        int objectivesCompleted,
        int penaltiesCount
) {
    public TrackPerformance {
        if (timeTakenSeconds < 0) {
            throw new IllegalArgumentException("timeTakenSeconds cannot be negative");
        }
        if (objectivesCompleted < 0) {
            throw new IllegalArgumentException("objectivesCompleted cannot be negative");
        }
        if (penaltiesCount < 0) {
            throw new IllegalArgumentException("penaltiesCount cannot be negative");
        }
    }

    public static TrackPerformance of(double timeTakenSeconds, int objectivesCompleted, int penaltiesCount) {
        return new TrackPerformance(timeTakenSeconds, objectivesCompleted, penaltiesCount);
    }
}
