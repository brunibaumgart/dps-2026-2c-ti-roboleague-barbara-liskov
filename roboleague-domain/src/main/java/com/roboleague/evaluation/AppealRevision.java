package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.appeal.AppealId;

import java.util.Objects;

/**
 * What an accepted appeal changes in an attempt: the corrected metrics, never a score. The attempt scores them
 * with its own rulebook.
 */
public record AppealRevision(AppealId appealId, RawMetrics metrics, AuditNote note) {
    public AppealRevision {
        Objects.requireNonNull(appealId, "appealId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(note, "note cannot be null");
    }
}
