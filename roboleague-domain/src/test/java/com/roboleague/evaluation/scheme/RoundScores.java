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

    static RoundScore round(String roundId, double total, double seconds, double deducted) {
        return withJudges(roundId, total, seconds, deducted, Map.of());
    }

    /**
     * A round that scored {@code total} after its deductions took away {@code deducted} points.
     */
    static RoundScore withJudges(String roundId, double total, double seconds, double deducted,
                                 Map<String, Double> judgeScores) {
        ScoreBreakdown breakdown = new ScoreBreakdown(
                RuleEvaluation.of(ScoreItem.of("Puntaje", "-", "-", total + deducted)),
                RuleEvaluation.empty(),
                RuleEvaluation.of(ScoreItem.of("Descuentos", "-", "-", -deducted)));
        RawMetrics metrics = RawMetrics.of(seconds, 0, 0, judgeScores);
        return new RoundScore(roundId, EvaluationSnapshot.of(metrics, breakdown));
    }

    static ChallengeScore allOf(RoundScore... rounds) {
        return new AllRounds().select(List.of(rounds));
    }
}
