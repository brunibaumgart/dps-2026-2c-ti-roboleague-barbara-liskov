package com.roboleague.usecase;

import com.roboleague.evaluation.RawMetrics;

import java.util.Objects;

/**
 * Input command for capturing and scoring an attempt result.
 */
public record CaptureAttemptResultCommand(
        String editionId,
        String attemptId,
        String teamId,
        String slotId,
        String roundId,
        int attemptNumber,
        RawMetrics metrics,
        String judgeId
) {
    public CaptureAttemptResultCommand {
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    public static CaptureAttemptResultCommand of(String editionId, String attemptId, String teamId,
                                                 String slotId, String roundId, int attemptNumber,
                                                 RawMetrics metrics, String judgeId) {
        return new CaptureAttemptResultCommand(
                editionId,
                attemptId,
                teamId,
                slotId,
                roundId,
                attemptNumber,
                metrics,
                judgeId
        );
    }
}
