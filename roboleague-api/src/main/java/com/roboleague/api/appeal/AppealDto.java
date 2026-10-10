package com.roboleague.api.appeal;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON view of an appeal. The aggregate never leaves the API as is.
 */
record AppealDto(String id, String attemptId, String teamId, String reason, String evidence, String status,
                 String reviewerId, LocalDateTime submittedAt, String resolutionNotes, LocalDateTime resolvedAt,
                 RevisedMetricsDto revisedMetrics) {

    static AppealDto from(Appeal appeal) {
        return new AppealDto(appeal.getAppealId().value(), appeal.getAttemptId().value(), appeal.getTeamId().value(),
                appeal.getReason(), appeal.getEvidenceDescription(), appeal.getStatusName(),
                valueOf(appeal.getReviewerId()), appeal.getSubmittedAt(), appeal.getResolutionNotes(),
                appeal.getResolvedAt(), RevisedMetricsDto.from(appeal.getRevisedMetrics()));
    }

    private static String valueOf(ActorId actor) {
        return actor == null ? null : actor.value();
    }

    /**
     * What an accepted appeal set the attempt's metrics to.
     */
    record RevisedMetricsDto(double timeSeconds, int objectives, int penalties, double consumption,
                             Map<String, Double> judgeScores, Map<String, Double> measurements) {

        static RevisedMetricsDto from(RawMetrics metrics) {
            if (metrics == null) {
                return null;
            }
            Map<String, Double> judgeScores = new TreeMap<>();
            metrics.judgeSubjectiveScores().forEach((judge, score) -> judgeScores.put(judge.value(), score));
            return new RevisedMetricsDto(metrics.timeTakenSeconds(), metrics.objectivesCompleted(),
                    metrics.penaltiesCount(), metrics.resourceConsumption(), judgeScores,
                    new TreeMap<>(metrics.customMetrics()));
        }
    }
}
