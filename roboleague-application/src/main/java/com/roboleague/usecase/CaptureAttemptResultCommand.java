package com.roboleague.usecase;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * Input command for capturing and scoring an attempt result.
 */
public record CaptureAttemptResultCommand(
        ChallengeId challengeId,
        String attemptId,
        String teamId,
        String slotId,
        String roundId,
        int attemptNumber,
        RawMetrics metrics,
        String judgeId
) {
    public CaptureAttemptResultCommand {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    public static CaptureAttemptResultCommand of(ChallengeId challengeId, String attemptId, String teamId,
                                                 String slotId, String roundId, int attemptNumber,
                                                 RawMetrics metrics, String judgeId) {
        return new CaptureAttemptResultCommand(
                challengeId,
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
