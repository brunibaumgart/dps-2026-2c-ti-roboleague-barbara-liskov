package com.roboleague.api.attempt;

import com.roboleague.evaluation.JudgeScores;
import com.roboleague.scheduling.JudgeId;

import java.util.HashMap;
import java.util.Map;

/**
 * What the judge panel sent, as it arrives in JSON: each judge's score by judge id and the named measurements the
 * judges count. Used by a result of an attempt and by the correction of an accepted appeal.
 */
public record JudgeScoresBody(Map<String, Double> scores, Map<String, Double> measurements) {

    public JudgeScores toReport() {
        if (scores == null) {
            throw new IllegalArgumentException("judge scores need scores");
        }
        Map<JudgeId, Double> byJudge = new HashMap<>();
        scores.forEach((id, score) -> byJudge.put(JudgeId.of(id), score));
        return new JudgeScores(byJudge, measurements != null ? measurements : Map.of());
    }
}
