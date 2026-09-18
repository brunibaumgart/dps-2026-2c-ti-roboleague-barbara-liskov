package com.roboleague.evaluation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record EvaluationFeedback(
        double resourceConsumption,
        Map<String, Double> judgeSubjectiveScores,
        Map<String, Double> customMetrics
) {
    public EvaluationFeedback {
        judgeSubjectiveScores = judgeSubjectiveScores != null
                ? Collections.unmodifiableMap(new HashMap<>(judgeSubjectiveScores))
                : Collections.emptyMap();
        customMetrics = customMetrics != null
                ? Collections.unmodifiableMap(new HashMap<>(customMetrics))
                : Collections.emptyMap();
    }

    public double averageJudgeScore() {
        if (judgeSubjectiveScores.isEmpty()) {
            return 0.0;
        }
        return judgeSubjectiveScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public static EvaluationFeedback empty() {
        return new EvaluationFeedback(0.0, Collections.emptyMap(), Collections.emptyMap());
    }

    public static EvaluationFeedback of(double resourceConsumption, Map<String, Double> judgeScores, Map<String, Double> customMetrics) {
        return new EvaluationFeedback(resourceConsumption, judgeScores, customMetrics);
    }

    public static EvaluationFeedback withJudgeScores(Map<String, Double> judgeScores) {
        return new EvaluationFeedback(0.0, judgeScores, Collections.emptyMap());
    }
}
