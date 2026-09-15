package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated score summary for a team in a category and edition.
 */
public record TeamScore(
        String teamId,
        String teamName,
        String categoryId,
        String editionId,
        double totalScore,
        double bestAttemptTime,
        int totalPenalties,
        double judgeSubjectiveScore,
        List<Attempt> evaluatedAttempts
) {
    public TeamScore {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(teamName, "teamName cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
        evaluatedAttempts = evaluatedAttempts != null ? Collections.unmodifiableList(evaluatedAttempts) : Collections.emptyList();
    }

    /**
     * Creates a consolidated TeamScore from a list of attempts by picking the best attempt score,
     * or consolidating all attempts.
     */
    public static TeamScore fromBestAttempt(String teamId, String teamName, String categoryId, String editionId, List<Attempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return new TeamScore(teamId, teamName, categoryId, editionId, 0.0, Double.MAX_VALUE, 0, 0.0, List.of());
        }

        // Find attempt with highest final score
        Attempt bestAttempt = attempts.stream()
                .filter(a -> a.getStatus() != Attempt.AttemptStatus.DISQUALIFIED)
                .max((a1, a2) -> Double.compare(a1.getFinalScore(), a2.getFinalScore()))
                .orElse(attempts.get(0));

        double score = bestAttempt.getFinalScore();
        RawMetrics metrics = bestAttempt.getLatestMetrics();
        double time = metrics != null ? metrics.timeTakenSeconds() : Double.MAX_VALUE;
        int penalties = metrics != null ? metrics.penaltiesCount() : 0;
        double judgeAvg = metrics != null ? metrics.getAverageJudgeScore() : 0.0;

        return new TeamScore(teamId, teamName, categoryId, editionId, score, time, penalties, judgeAvg, attempts);
    }
}
