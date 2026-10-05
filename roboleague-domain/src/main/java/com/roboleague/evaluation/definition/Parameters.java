package com.roboleague.evaluation.definition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named numeric parameters of a rule or strategy, as they travel through the API and the database.
 */
public record Parameters(Map<String, Double> values) {
    public Parameters {
        Objects.requireNonNull(values, "values cannot be null");
        values.forEach((name, value) -> {
            if (name == null || value == null) {
                throw new IllegalArgumentException("parameter '" + name + "' has no value");
            }
        });
        values = Map.copyOf(values);
    }

    public static Parameters none() {
        return new Parameters(Map.of());
    }

    public static Parameters of(Map<String, Double> values) {
        return new Parameters(values);
    }

    public Parameters with(String name, double value) {
        Map<String, Double> extended = new HashMap<>(values);
        extended.put(name, value);
        return new Parameters(extended);
    }

    public double number(String name) {
        Double value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter '" + name + "'");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("parameter '" + name + "' must be a finite number: " + value);
        }
        return value;
    }

    /**
     * Names in these parameters that the accepted ones do not have, sorted.
     */
    public List<String> unknownTo(Parameters accepted) {
        return values.keySet().stream().filter(name -> !accepted.values().containsKey(name)).sorted().toList();
    }

    public int whole(String name) {
        double value = number(name);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("parameter '" + name + "' must be a whole number: " + value);
        }
        return (int) value;
    }
}
