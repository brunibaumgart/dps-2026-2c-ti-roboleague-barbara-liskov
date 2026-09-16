package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

/**
 * Initial state of an appeal. Awaiting evaluation by the arbitration committee.
 */
public class PendingAppealState implements AppealState {

    @Override
    public String getStateName() {
        return "PENDING";
    }

    @Override
    public void beginReview(Appeal appeal, String reviewerId) {
        appeal.setReviewerId(reviewerId);
        appeal.transitionToState(new UnderReviewAppealState());
    }

    @Override
    public void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        throw new IllegalStateException("Appeal must be placed under review before being accepted");
    }

    @Override
    public void reject(Appeal appeal, String resolutionNotes, String reviewerId) {
        throw new IllegalStateException("Appeal must be placed under review before being rejected");
    }

    @Override
    public boolean isPending() {
        return true;
    }

    @Override
    public boolean isUnderReview() {
        return false;
    }

    @Override
    public boolean isAccepted() {
        return false;
    }

    @Override
    public boolean isRejected() {
        return false;
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public boolean canPublishOfficialRanking() {
        return false;
    }
}
