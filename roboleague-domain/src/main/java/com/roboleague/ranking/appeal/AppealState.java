package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

/**
 * State pattern interface for the Appeal lifecycle.
 */
public interface AppealState {
    String getStateName();

    void beginReview(Appeal appeal, String reviewerId);

    void accept(Appeal appeal, String resolutionNotes, RawMetrics revisedMetrics, String reviewerId);

    void reject(Appeal appeal, String resolutionNotes, String reviewerId);
}
