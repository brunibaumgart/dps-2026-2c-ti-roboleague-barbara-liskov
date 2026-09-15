package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

/**
 * Terminal state representing an appeal that was dismissed/rejected.
 */
public class RejectedAppealState implements AppealState {

    @Override
    public String getStateName() {
        return "REJECTED";
    }

    @Override
    public void beginReview(Appeal appeal, String reviewerId) {
        throw new IllegalStateException("Cannot review an already rejected appeal");
    }

    @Override
    public void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        throw new IllegalStateException("Cannot accept an already rejected appeal");
    }

    @Override
    public void reject(Appeal appeal, String resolutionNotes, String reviewerId) {
        throw new IllegalStateException("Appeal has already been rejected");
    }
}
