package com.roboleague.api.graphql.view;

import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.support.ActorId;

/**
 * GraphQL view of an appeal. Reviewer and resolution fields are null until they happen. {@code attempt} and
 * {@code team} are resolved in batch from the ids kept here.
 */
public record AppealView(String id, String attemptId, String teamId, String reason, String evidence, String status,
                         String reviewerId, String submittedAt, String resolutionNotes, String resolvedAt) {

    public static AppealView from(Appeal appeal) {
        return new AppealView(appeal.getAppealId().value(), appeal.getAttemptId().value(), appeal.getTeamId().value(),
                appeal.getReason(), appeal.getEvidenceDescription(), appeal.getStatusName(),
                valueOf(appeal.getReviewerId()), Dates.iso(appeal.getSubmittedAt()), appeal.getResolutionNotes(),
                Dates.iso(appeal.getResolvedAt()));
    }

    private static String valueOf(ActorId actor) {
        return actor == null ? null : actor.value();
    }
}
