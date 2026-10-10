package com.roboleague.api.attempt;

import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.TrackPerformance;

import java.util.Map;

/**
 * What the track's sensors measured, as it arrives in JSON: with a result of an attempt or with the correction of
 * an accepted appeal.
 */
public record MeasurementsBody(Double timeSeconds, Integer objectives, Integer penalties, Double consumption,
                               Map<String, Double> measurements) {

    public Measurements toReport() {
        if (timeSeconds == null || objectives == null || penalties == null || consumption == null) {
            throw new IllegalArgumentException("measurements need timeSeconds, objectives, penalties and consumption");
        }
        return new Measurements(new TrackPerformance(timeSeconds, objectives, penalties), consumption,
                measurements != null ? measurements : Map.of());
    }
}
