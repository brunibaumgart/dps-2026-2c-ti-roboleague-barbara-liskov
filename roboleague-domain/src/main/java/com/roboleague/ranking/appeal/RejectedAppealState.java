package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;

/**
 * Terminal state representing an appeal that was dismissed/rejected.
 */
public class RejectedAppealState implements AppealState {

    @Override
    public String getStateName() {
        return "REJECTED";
    }

    @Override
    public void beginReview(Appeal appeal, ActorId reviewerId) {
        throw new IllegalStateException("Cannot review an already rejected appeal");
    }

    @Override
    public void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, ActorId reviewerId, LocalDateTime resolvedAt) {
        throw new IllegalStateException("Cannot accept an already rejected appeal");
    }

    @Override
    public void reject(Appeal appeal, String resolutionNotes, ActorId reviewerId, LocalDateTime resolvedAt) {
        throw new IllegalStateException("Appeal has already been rejected");
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
        return false;
    }

    @Override
    public boolean isRejected() {
        return true;
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
