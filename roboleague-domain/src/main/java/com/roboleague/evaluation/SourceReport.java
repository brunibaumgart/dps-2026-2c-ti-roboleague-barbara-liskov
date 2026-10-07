package com.roboleague.evaluation;

import java.util.Map;

/**
 * What one source sent for an attempt (F3): the automatic measurements of the track or the judge panel's scores.
 * Each source arrives on its own; the attempt adds them up once every source its rulebook needs is in.
 */
public sealed interface SourceReport permits Measurements, JudgeScores {

    ResultSource source();

    /**
     * The named measurements of this source, which its rulebook declares and checks.
     */
    Map<String, Double> named();

    /**
     * The captured metrics with what this source measured added to them.
     */
    RawMetrics addTo(RawMetrics metrics);
}
