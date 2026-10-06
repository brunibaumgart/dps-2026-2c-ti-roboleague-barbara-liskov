package com.roboleague.evaluation;

import java.util.Objects;

/**
 * What one source sent for an attempt and the judge who loaded it.
 */
public record SourceDelivery(SourceReport report, String judgeId) {
    public SourceDelivery {
        Objects.requireNonNull(report, "report cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        if (judgeId.isBlank()) {
            throw new IllegalArgumentException("judgeId cannot be blank");
        }
    }

    public ResultSource source() {
        return report.source();
    }
}
