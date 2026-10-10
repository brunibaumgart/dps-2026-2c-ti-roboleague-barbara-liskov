package com.roboleague.ranking;

import com.roboleague.evaluation.audit.AuditNote;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A version made official: when, by whom and why. Publishing a later version replaces it as the official one,
 * but the record stays.
 */
public record OfficialPublication(int version, LocalDateTime publishedAt, AuditNote note) {
    public OfficialPublication {
        if (version < 1) {
            throw new IllegalArgumentException("standings versions start at 1: " + version);
        }
        Objects.requireNonNull(publishedAt, "publishedAt cannot be null");
        Objects.requireNonNull(note, "note cannot be null");
    }
}
