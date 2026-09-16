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
        TeamEntryHeader header,
        PerformanceSummary performance,
        List<Attempt> evaluatedAttempts
) {
    public TeamScore {
        Objects.requireNonNull(header, "header cannot be null");
        Objects.requireNonNull(performance, "performance cannot be null");
        evaluatedAttempts = evaluatedAttempts != null ? Collections.unmodifiableList(evaluatedAttempts) : Collections.emptyList();
    }

    public String teamId() {
        return header.team().teamId();
    }

    public String teamName() {
        return header.team().teamName();
    }

    public String categoryId() {
        return header.context().categoryId();
    }

    public String editionId() {
        return header.context().editionId();
    }

    public double totalScore() {
        return performance.totalScore();
    }

    public double bestAttemptTime() {
        return performance.bestAttemptTime();
    }

    public int totalPenalties() {
        return performance.totalPenalties();
    }

    public double judgeSubjectiveScore() {
        return performance.judgeSubjectiveScore();
    }

    public boolean isTiedWith(TeamScore other) {
        if (other == null) return false;
        return performance.isTiedWith(other.performance);
    }

    public static TeamScore of(TeamEntryHeader header, PerformanceSummary performance, List<Attempt> evaluatedAttempts) {
        return new TeamScore(header, performance, evaluatedAttempts);
    }

    public static TeamScore of(String teamId, String teamName, String categoryId, String editionId,
                               PerformanceSummary performance, List<Attempt> evaluatedAttempts) {
        return new TeamScore(
                TeamEntryHeader.of(teamId, teamName, categoryId, editionId),
                performance,
                evaluatedAttempts
        );
    }

    public static TeamScore fromBestAttempt(String teamId, String teamName, String categoryId, String editionId, List<Attempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return TeamScore.of(
                    teamId, teamName, categoryId, editionId,
                    PerformanceSummary.of(0.0, Double.MAX_VALUE, 0, 0.0),
                    List.of()
            );
        }

        Attempt bestAttempt = attempts.stream()
                .filter(a -> a.getStatus() != Attempt.AttemptStatus.DISQUALIFIED)
                .max((a1, a2) -> Double.compare(a1.getFinalScore(), a2.getFinalScore()))
                .orElse(attempts.get(0));

        double score = bestAttempt.getFinalScore();
        RawMetrics metrics = bestAttempt.getLatestMetrics();
        double time = metrics != null ? metrics.timeTakenSeconds() : Double.MAX_VALUE;
        int penalties = metrics != null ? metrics.penaltiesCount() : 0;
        double judgeAvg = metrics != null ? metrics.getAverageJudgeScore() : 0.0;

        return TeamScore.of(
                teamId, teamName, categoryId, editionId,
                PerformanceSummary.of(score, time, penalties, judgeAvg),
                attempts
        );
    }
}
