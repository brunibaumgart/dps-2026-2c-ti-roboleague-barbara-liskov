package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * Input command for capturing and scoring an attempt result: the challenge, the turn it was run in, what was
 * measured and the judge who captured it.
 */
public record CaptureAttemptResultCommand(
        ChallengeId challengeId,
        AttemptIdentity attempt,
        RawMetrics metrics,
        String judgeId
) {
    public CaptureAttemptResultCommand {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(attempt, "attempt cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }
}
