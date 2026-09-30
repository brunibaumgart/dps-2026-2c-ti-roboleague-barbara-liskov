package com.roboleague.repository.jpa;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.ranking.appeal.AcceptedAppealState;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealClaim;
import com.roboleague.ranking.appeal.AppealProgress;
import com.roboleague.ranking.appeal.AppealState;
import com.roboleague.ranking.appeal.AppealTarget;
import com.roboleague.ranking.appeal.PendingAppealState;
import com.roboleague.ranking.appeal.RejectedAppealState;
import com.roboleague.ranking.appeal.UnderReviewAppealState;
import com.roboleague.repository.jpa.AppealJpaEntity.MetricsJson;

/**
 * Anti-corruption layer between the appeals table and the {@link Appeal} aggregate.
 */
final class AppealMapper {

    private AppealMapper() {
    }

    static AppealJpaEntity toEntity(Appeal appeal) {
        AppealJpaEntity entity = new AppealJpaEntity();
        entity.id = appeal.getAppealId();
        entity.attemptId = appeal.getAttemptId();
        entity.teamId = appeal.getTeamId();
        entity.reason = appeal.getReason();
        entity.evidenceDescription = appeal.getEvidenceDescription();
        entity.status = appeal.getStatusName();
        entity.submittedAt = appeal.getSubmittedAt();
        entity.reviewerId = appeal.getReviewerId();
        entity.resolutionNotes = appeal.getResolutionNotes();
        entity.revisedMetrics = appeal.getRevisedMetrics() == null ? null : toJson(appeal.getRevisedMetrics());
        entity.resolvedAt = appeal.getResolvedAt();
        return entity;
    }

    static Appeal toDomain(AppealJpaEntity entity) {
        return Appeal.restore(
                AppealTarget.of(entity.id, entity.attemptId, entity.teamId),
                AppealClaim.of(entity.reason, entity.evidenceDescription),
                new AppealProgress(
                        stateNamed(entity.status),
                        entity.submittedAt,
                        entity.reviewerId,
                        entity.resolutionNotes,
                        entity.revisedMetrics == null ? null : toMetrics(entity.revisedMetrics),
                        entity.resolvedAt));
    }

    private static AppealState stateNamed(String status) {
        return switch (status) {
            case "PENDING" -> new PendingAppealState();
            case "UNDER_REVIEW" -> new UnderReviewAppealState();
            case "ACCEPTED" -> new AcceptedAppealState();
            case "REJECTED" -> new RejectedAppealState();
            default -> throw new IllegalStateException("Unknown stored appeal status: " + status);
        };
    }

    private static MetricsJson toJson(RawMetrics metrics) {
        return new MetricsJson(
                metrics.timeTakenSeconds(),
                metrics.objectivesCompleted(),
                metrics.penaltiesCount(),
                metrics.resourceConsumption(),
                metrics.judgeSubjectiveScores(),
                metrics.customMetrics());
    }

    private static RawMetrics toMetrics(MetricsJson json) {
        return RawMetrics.of(
                TrackPerformance.of(json.timeTakenSeconds(), json.objectivesCompleted(), json.penaltiesCount()),
                EvaluationFeedback.of(json.resourceConsumption(), json.judgeSubjectiveScores(), json.customMetrics()));
    }
}
