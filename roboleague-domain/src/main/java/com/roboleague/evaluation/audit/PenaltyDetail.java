package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Value object specifying count of fouls and justification.
 */
public record PenaltyDetail(int additionalPenalties, String reason) {
    public PenaltyDetail {
        Objects.requireNonNull(reason, "reason cannot be null");
        if (additionalPenalties <= 0) {
            throw new IllegalArgumentException("additionalPenalties must be positive");
        }
    }

    public static PenaltyDetail of(int additionalPenalties, String reason) {
        return new PenaltyDetail(additionalPenalties, reason);
    }
}
