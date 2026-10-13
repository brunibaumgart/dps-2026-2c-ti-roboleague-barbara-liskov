package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;

import java.util.Objects;

/**
 * A milestone is reached when a measurement gets to its threshold (e.g. "llegar a la zona de rescate").
 */
public record Milestone(Metric metric, double threshold) {
    public Milestone {
        Objects.requireNonNull(metric, "metric cannot be null");
        if (!Double.isFinite(threshold) || threshold < 0) {
            throw new IllegalArgumentException("threshold must be a finite, non-negative number: " + threshold);
        }
    }

    public boolean reachedWith(double measured) {
        return measured >= threshold;
    }
}
