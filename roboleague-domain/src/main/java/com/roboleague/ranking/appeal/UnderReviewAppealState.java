package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

import java.time.LocalDateTime;

/**
 * State representing an appeal being actively evaluated by an assigned judge/arbitrator.
 */
public class UnderReviewAppealState implements AppealState {

    @Override
    public String getStateName() {
        return "UNDER_REVIEW";
    }

    @Override
    public void beginReview(Appeal appeal, String reviewerId) {
        throw new IllegalStateException("Appeal is already under review by " + appeal.getReviewerId());
    }

    @Override
    public void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        if (revisedMetrics == null) {
            throw new IllegalArgumentException("Revised metrics are mandatory when accepting an appeal");
        }
        appeal.setResolution(resolutionNotes, revisedMetrics, reviewerId);
        appeal.setResolvedAt(LocalDateTime.now());
        appeal.transitionToState(new AcceptedAppealState());
    }

    @Override
    public void reject(Appeal appeal, String resolutionNotes, String reviewerId) {
        appeal.setResolution(resolutionNotes, null, reviewerId);
        appeal.setResolvedAt(LocalDateTime.now());
        appeal.transitionToState(new RejectedAppealState());
    }
}
