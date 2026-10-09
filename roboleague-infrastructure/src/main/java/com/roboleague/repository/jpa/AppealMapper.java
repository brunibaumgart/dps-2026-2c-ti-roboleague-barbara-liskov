package com.roboleague.repository.jpa;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.AcceptedAppealState;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealClaim;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.ranking.appeal.AppealProgress;
import com.roboleague.ranking.appeal.AppealState;
import com.roboleague.ranking.appeal.AppealTarget;
import com.roboleague.ranking.appeal.PendingAppealState;
import com.roboleague.ranking.appeal.RejectedAppealState;
import com.roboleague.ranking.appeal.UnderReviewAppealState;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.TeamId;

/**
 * Anti-corruption layer between the appeals table and the {@link Appeal} aggregate.
 */
final class AppealMapper {

    private AppealMapper() {
    }

    static AppealJpaEntity toEntity(Appeal appeal) {
        AppealJpaEntity entity = new AppealJpaEntity();
        entity.id = appeal.getAppealId().value();
        entity.attemptId = appeal.getAttemptId().value();
        entity.teamId = appeal.getTeamId().value();
        entity.reason = appeal.getReason();
        entity.evidenceDescription = appeal.getEvidenceDescription();
        entity.status = appeal.getStatusName();
        entity.submittedAt = appeal.getSubmittedAt();
        entity.reviewerId = appeal.getReviewerId() == null ? null : appeal.getReviewerId().value();
        entity.resolutionNotes = appeal.getResolutionNotes();
        entity.revisedMetrics = appeal.getRevisedMetrics() == null ? null : MetricsJson.of(appeal.getRevisedMetrics());
        entity.resolvedAt = appeal.getResolvedAt();
        return entity;
    }

    static Appeal toDomain(AppealJpaEntity entity) {
        return Appeal.restore(
                AppealTarget.of(AppealId.of(entity.id), AttemptId.parse(entity.attemptId), TeamId.of(entity.teamId)),
                AppealClaim.of(entity.reason, entity.evidenceDescription),
                new AppealProgress(
                        stateNamed(entity.status),
                        entity.submittedAt,
                        entity.reviewerId == null ? null : ActorId.of(entity.reviewerId),
                        entity.resolutionNotes,
                        entity.revisedMetrics == null ? null : entity.revisedMetrics.toMetrics(),
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
}
