package com.roboleague.evaluation.audit;

import com.roboleague.support.ActorId;

import java.util.Objects;

/**
 * Who changed an attempt and why: every revision after the first one carries it.
 */
public record AuditNote(ActorId authorId, String reason) {
    public AuditNote {
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }

    public static AuditNote of(ActorId authorId, String reason) {
        return new AuditNote(authorId, reason);
    }
}
