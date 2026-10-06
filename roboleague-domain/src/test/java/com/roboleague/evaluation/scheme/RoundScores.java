package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoreItem;
import com.roboleague.evaluation.audit.EvaluationSnapshot;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.List;
import java.util.Map;

final class RoundScores {

    private RoundScores() {
    }

    static RoundScore round(String roundId, double total) {
        return round(roundId, total, 60.0, 0);
    }

    static RoundScore round(String roundId, double total, double seconds, int penalties) {
        return withJudges(roundId, total, seconds, penalties, Map.of());
    }

    static RoundScore withJudges(String roundId, double total, double seconds, int penalties,
                                 Map<String, Double> judgeScores) {
        ScoreBreakdown breakdown = new ScoreBreakdown(RuleEvaluation.of(ScoreItem.of("Puntaje", "-", "-", total)),
                RuleEvaluation.empty(), RuleEvaluation.empty());
        RawMetrics metrics = RawMetrics.of(seconds, 0, penalties, judgeScores);
        return new RoundScore(roundId, EvaluationSnapshot.of(metrics, breakdown));
    }

    static ChallengeScore allOf(RoundScore... rounds) {
        return new AllRounds().select(List.of(rounds));
    }
}
