package com.roboleague.evaluation;

/**
 * Unit in which a declared metric is measured. A count only takes whole numbers.
 */
public enum MeasurementUnit {
    COUNT {
        @Override
        public boolean accepts(double value) {
            return value == Math.rint(value);
        }
    },
    SECONDS,
    METERS,
    RATIO,
    POINTS;

    public boolean accepts(double value) {
        return true;
    }
}
