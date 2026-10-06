package com.roboleague.evaluation;

import java.util.List;

/**
 * Outcome of checking what one source sent for an attempt against the metrics its rulebook declares.
 */
public sealed interface MeasurementCheck permits MeasurementCheck.Accepted, MeasurementCheck.Rejected {

    record Accepted() implements MeasurementCheck {
    }

    record Rejected(List<String> problems) implements MeasurementCheck {
        public Rejected {
            problems = List.copyOf(problems);
        }
    }
}
