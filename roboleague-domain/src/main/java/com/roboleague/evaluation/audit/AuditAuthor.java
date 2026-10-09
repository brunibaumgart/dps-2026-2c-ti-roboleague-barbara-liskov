package com.roboleague.evaluation.audit;

import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing the responsible author or judge and the exact timestamp.
 */
public record AuditAuthor(ActorId authorOrJudgeId, LocalDateTime timestamp) {
    public AuditAuthor {
        Objects.requireNonNull(authorOrJudgeId, "authorOrJudgeId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public static AuditAuthor of(ActorId authorOrJudgeId, LocalDateTime timestamp) {
        return new AuditAuthor(authorOrJudgeId, timestamp);
    }

}
