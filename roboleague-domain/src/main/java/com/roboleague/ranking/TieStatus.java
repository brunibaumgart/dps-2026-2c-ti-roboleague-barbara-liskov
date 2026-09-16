package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object specifying tie status and tie-breaker justification for a ranking entry.
 */
public record TieStatus(boolean isTiedWithPrevious, String tieBreakerExplanation) {
    public TieStatus {
        tieBreakerExplanation = tieBreakerExplanation != null ? tieBreakerExplanation : "";
    }

    public static TieStatus untied(String explanation) {
        return new TieStatus(false, explanation);
    }

    public static TieStatus tied(String explanation) {
        return new TieStatus(true, explanation);
    }
}
