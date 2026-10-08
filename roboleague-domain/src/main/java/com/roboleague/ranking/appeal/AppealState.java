package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;

/**
 * State pattern interface for the Appeal lifecycle.
 */
public interface AppealState {
    String getStateName();

    void beginReview(Appeal appeal, ActorId reviewerId);

    void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, ActorId reviewerId, LocalDateTime resolvedAt);

    void reject(Appeal appeal, String resolutionNotes, ActorId reviewerId, LocalDateTime resolvedAt);

    boolean isPending();

    boolean isUnderReview();

    boolean isAccepted();

    boolean isRejected();

    boolean isResolved();

    boolean canPublishOfficialRanking();
}
