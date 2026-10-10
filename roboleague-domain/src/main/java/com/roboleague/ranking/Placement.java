package com.roboleague.ranking;

import java.util.Objects;

/**
 * Where a team ended up and why: against the team right above, which criterion of the chain decided, or that
 * every criterion tied and they share the position.
 */
public record Placement(int position, String explanation) {
    public Placement {
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1: " + position);
        }
        Objects.requireNonNull(explanation, "explanation cannot be null");
    }
}
