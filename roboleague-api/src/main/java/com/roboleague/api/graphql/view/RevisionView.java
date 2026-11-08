package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.audit.AttemptScoreSnapshot;

/**
 * GraphQL view of one revision of a score: who changed it, why, with which rulebook version and to what total.
 */
public record RevisionView(int number, int rulebookVersion, String authorId, String reason, double total,
                           String timestamp) {

    public static RevisionView from(AttemptScoreSnapshot revision) {
        return new RevisionView(revision.revisionNumber(), revision.rulebookVersion().number(),
                revision.authorOrJudgeId().value(), revision.reason(), revision.breakdown().totalScore().doubleValue(),
                Dates.iso(revision.timestamp()));
    }
}
