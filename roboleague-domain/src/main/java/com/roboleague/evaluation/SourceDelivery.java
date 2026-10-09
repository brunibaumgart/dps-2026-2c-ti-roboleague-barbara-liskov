package com.roboleague.evaluation;

import com.roboleague.scheduling.JudgeId;

import java.util.Objects;

/**
 * What one source sent for an attempt and the judge who loaded it.
 */
public record SourceDelivery(SourceReport report, JudgeId judgeId) {
    public SourceDelivery {
        Objects.requireNonNull(report, "report cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    public ResultSource source() {
        return report.source();
    }
}
