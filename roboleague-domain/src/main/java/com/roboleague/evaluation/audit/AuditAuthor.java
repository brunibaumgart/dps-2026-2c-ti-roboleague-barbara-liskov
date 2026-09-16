package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing the responsible author or judge and the exact timestamp.
 */
public record AuditAuthor(String authorOrJudgeId, LocalDateTime timestamp) {
    public AuditAuthor {
        Objects.requireNonNull(authorOrJudgeId, "authorOrJudgeId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public static AuditAuthor of(String authorOrJudgeId, LocalDateTime timestamp) {
        return new AuditAuthor(authorOrJudgeId, timestamp);
    }

    public static AuditAuthor now(String authorOrJudgeId) {
        return new AuditAuthor(authorOrJudgeId, LocalDateTime.now());
    }
}
