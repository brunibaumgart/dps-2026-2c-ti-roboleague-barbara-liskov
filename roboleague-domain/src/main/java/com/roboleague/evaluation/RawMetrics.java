package com.roboleague.evaluation;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Raw metrics captured during an attempt on the track/arena.
 */
public record RawMetrics(
        double timeTakenSeconds,
        int objectivesCompleted,
        int penaltiesCount,
        double resourceConsumption,
        Map<String, Double> judgeSubjectiveScores,
        Map<String, Double> customMetrics
) {
    public RawMetrics {
        if (timeTakenSeconds < 0) {
            throw new IllegalArgumentException("timeTakenSeconds cannot be negative");
        }
        if (objectivesCompleted < 0) {
            throw new IllegalArgumentException("objectivesCompleted cannot be negative");
        }
        if (penaltiesCount < 0) {
            throw new IllegalArgumentException("penaltiesCount cannot be negative");
        }
        judgeSubjectiveScores = judgeSubjectiveScores != null ? Collections.unmodifiableMap(new HashMap<>(judgeSubjectiveScores)) : Collections.emptyMap();
        customMetrics = customMetrics != null ? Collections.unmodifiableMap(new HashMap<>(customMetrics)) : Collections.emptyMap();
    }

    public static RawMetrics of(double timeTakenSeconds, int objectivesCompleted, int penaltiesCount) {
        return new RawMetrics(timeTakenSeconds, objectivesCompleted, penaltiesCount, 0.0, Map.of(), Map.of());
    }

    public static RawMetrics of(double timeTakenSeconds, int objectivesCompleted, int penaltiesCount, Map<String, Double> judgeScores) {
        return new RawMetrics(timeTakenSeconds, objectivesCompleted, penaltiesCount, 0.0, judgeScores, Map.of());
    }

    public double getAverageJudgeScore() {
        if (judgeSubjectiveScores.isEmpty()) {
            return 0.0;
        }
        return judgeSubjectiveScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
