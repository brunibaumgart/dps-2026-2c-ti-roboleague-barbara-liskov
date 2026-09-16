package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

/**
 * Terminal state representing an appeal that was upheld/accepted.
 */
public class AcceptedAppealState implements AppealState {

    @Override
    public String getStateName() {
        return "ACCEPTED";
    }

    @Override
    public void beginReview(Appeal appeal, String reviewerId) {
        throw new IllegalStateException("Cannot re-review an already accepted appeal");
    }

    @Override
    public void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        throw new IllegalStateException("Appeal has already been accepted");
    }

    @Override
    public void reject(Appeal appeal, String resolutionNotes, String reviewerId) {
        throw new IllegalStateException("Cannot reject an already accepted appeal");
    }

    @Override
    public boolean isPending() {
        return false;
    }

    @Override
    public boolean isUnderReview() {
        return false;
    }

    @Override
    public boolean isAccepted() {
        return true;
    }

    @Override
    public boolean isRejected() {
        return false;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public boolean canPublishOfficialRanking() {
        return true;
    }
}
