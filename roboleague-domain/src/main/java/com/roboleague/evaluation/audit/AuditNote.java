package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Who changed an attempt and why: every revision after the first one carries it.
 */
public record AuditNote(String authorId, String reason) {
    public AuditNote {
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (authorId.isBlank()) {
            throw new IllegalArgumentException("authorId cannot be blank");
        }
    }

    public static AuditNote of(String authorId, String reason) {
        return new AuditNote(authorId, reason);
    }
}
