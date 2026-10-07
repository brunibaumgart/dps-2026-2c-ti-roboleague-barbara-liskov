package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AuditNote;

import java.util.Objects;

/**
 * What an accepted appeal changes in an attempt: the corrected metrics, never a score. The attempt scores them
 * with its own rulebook.
 */
public record AppealRevision(String appealId, RawMetrics metrics, AuditNote note) {
    public AppealRevision {
        Objects.requireNonNull(appealId, "appealId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(note, "note cannot be null");
    }
}
