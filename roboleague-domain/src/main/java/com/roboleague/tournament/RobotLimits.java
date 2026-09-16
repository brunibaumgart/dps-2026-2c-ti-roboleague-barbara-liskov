package com.roboleague.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object representing technical physical limits of a robot for a category.
 */
public record RobotLimits(Weight maxWeight, Dimensions maxDimensions) {
    public RobotLimits {
        Objects.requireNonNull(maxWeight, "maxWeight cannot be null");
        Objects.requireNonNull(maxDimensions, "maxDimensions cannot be null");
    }

    public boolean allows(RobotSpecification spec) {
        Objects.requireNonNull(spec, "spec cannot be null");
        return !spec.weight().isGreaterThan(maxWeight) && spec.dimensions().fitsWithin(maxDimensions);
    }

    public List<String> checkViolations(RobotSpecification spec) {
        Objects.requireNonNull(spec, "spec cannot be null");
        List<String> violations = new ArrayList<>();
        if (spec.weight().isGreaterThan(maxWeight)) {
            violations.add("Robot weight (" + spec.weight().grams() + "g) exceeds category limit (" + maxWeight.grams() + "g)");
        }
        if (spec.dimensions().lengthMm() > maxDimensions.lengthMm()) {
            violations.add("Robot length (" + spec.dimensions().lengthMm() + "mm) exceeds category limit (" + maxDimensions.lengthMm() + "mm)");
        }
        if (spec.dimensions().widthMm() > maxDimensions.widthMm()) {
            violations.add("Robot width (" + spec.dimensions().widthMm() + "mm) exceeds category limit (" + maxDimensions.widthMm() + "mm)");
        }
        if (spec.dimensions().heightMm() > maxDimensions.heightMm()) {
            violations.add("Robot height (" + spec.dimensions().heightMm() + "mm) exceeds category limit (" + maxDimensions.heightMm() + "mm)");
        }
        return Collections.unmodifiableList(violations);
    }

    public static RobotLimits of(double maxWeightGrams, double lengthMm, double widthMm, double heightMm) {
        return new RobotLimits(new Weight(maxWeightGrams), new Dimensions(lengthMm, widthMm, heightMm));
    }
}
