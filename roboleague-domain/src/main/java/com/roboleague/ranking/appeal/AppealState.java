package com.roboleague.ranking.appeal;

import java.time.LocalDateTime;
import com.roboleague.evaluation.RawMetrics;

/**
 * State pattern interface for the Appeal lifecycle.
 */
public interface AppealState {
    String getStateName();

    void beginReview(Appeal appeal, String reviewerId);

    void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId, LocalDateTime resolvedAt);

    void reject(Appeal appeal, String resolutionNotes, String reviewerId, LocalDateTime resolvedAt);

    boolean isPending();

    boolean isUnderReview();

    boolean isAccepted();

    boolean isRejected();

    boolean isResolved();

    boolean canPublishOfficialRanking();
}
