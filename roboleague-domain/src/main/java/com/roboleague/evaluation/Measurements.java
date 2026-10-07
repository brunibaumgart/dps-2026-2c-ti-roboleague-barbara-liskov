package com.roboleague.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * What the track's sensors measured in an attempt: time, objectives and faults, the resource consumption and the
 * named sensor measurements its rulebook declares.
 */
public record Measurements(TrackPerformance track, double consumption, Map<String, Double> named)
        implements SourceReport {

    public Measurements {
        Objects.requireNonNull(track, "track cannot be null");
        if (!Double.isFinite(consumption) || consumption < 0) {
            throw new IllegalArgumentException("consumption must be a finite, non-negative number: " + consumption);
        }
        Objects.requireNonNull(named, "named measurements cannot be null");
        named.forEach((name, value) -> {
            if (value == null) {
                throw new IllegalArgumentException("measurement '" + name + "' has no value");
            }
        });
        named = Map.copyOf(named);
    }

    @Override
    public ResultSource source() {
        return ResultSource.AUTOMATIC_MEASUREMENTS;
    }

    @Override
    public RawMetrics addTo(RawMetrics metrics) {
        return metrics.withTrack(track, consumption, named);
    }
}
