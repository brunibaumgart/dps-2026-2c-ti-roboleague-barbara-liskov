package com.roboleague.evaluation;

import com.roboleague.scheduling.JudgeId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record EvaluationFeedback(
        double resourceConsumption,
        Map<JudgeId, Double> judgeSubjectiveScores,
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

    /**
     * The judges' average, added up in decimal so that the same scores give the same average whichever judge sent
     * each one.
     */
    public double averageJudgeScore() {
        if (judgeSubjectiveScores.isEmpty()) {
            return 0.0;
        }
        BigDecimal sum = judgeSubjectiveScores.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(judgeSubjectiveScores.size()), MathContext.DECIMAL64).doubleValue();
    }

    public static EvaluationFeedback empty() {
        return new EvaluationFeedback(0.0, Collections.emptyMap(), Collections.emptyMap());
    }

    public static EvaluationFeedback of(double resourceConsumption, Map<JudgeId, Double> judgeScores, Map<String, Double> customMetrics) {
        return new EvaluationFeedback(resourceConsumption, judgeScores, customMetrics);
    }
}
