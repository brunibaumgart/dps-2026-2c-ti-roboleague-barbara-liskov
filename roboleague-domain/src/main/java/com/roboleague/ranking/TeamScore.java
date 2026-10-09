package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public TeamId teamId() {
        return header.team().teamId();
    }

    public String teamName() {
        return header.team().teamName();
    }

    public CategoryId categoryId() {
        return header.context().categoryId();
    }

    public EditionId editionId() {
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

    public static TeamScore of(TeamId teamId, String teamName, CategoryId categoryId, EditionId editionId,
                               PerformanceSummary performance, List<Attempt> evaluatedAttempts) {
        return new TeamScore(
                TeamEntryHeader.of(teamId, teamName, categoryId, editionId),
                performance,
                evaluatedAttempts
        );
    }

    /**
     * Scores the team with its best attempt that counts. Attempts not scored yet or disqualified do not count, so a
     * team without one that counts scores nothing.
     */
    public static TeamScore fromBestAttempt(TeamId teamId, String teamName, CategoryId categoryId, EditionId editionId, List<Attempt> attempts) {
        List<Attempt> candidates = attempts != null ? attempts : List.of();
        Optional<Attempt> countedAttempt = candidates.stream()
                .filter(a -> a.countableScore().isPresent())
                .max(Comparator.comparingDouble(a -> a.countableScore().orElseThrow().totalScore()));
        if (countedAttempt.isEmpty()) {
            return TeamScore.of(
                    teamId, teamName, categoryId, editionId,
                    PerformanceSummary.of(0.0, Double.MAX_VALUE, 0, 0.0),
                    candidates
            );
        }

        Attempt bestAttempt = countedAttempt.orElseThrow();
        double score = bestAttempt.countableScore().orElseThrow().totalScore();
        RawMetrics metrics = bestAttempt.getLatestMetrics();
        double time = metrics != null ? metrics.timeTakenSeconds() : Double.MAX_VALUE;
        int penalties = metrics != null ? metrics.penaltiesCount() : 0;
        double judgeAvg = metrics != null ? metrics.getAverageJudgeScore() : 0.0;

        return TeamScore.of(
                teamId, teamName, categoryId, editionId,
                PerformanceSummary.of(score, time, penalties, judgeAvg),
                candidates
        );
    }
}
