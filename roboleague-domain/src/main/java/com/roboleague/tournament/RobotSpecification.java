package com.roboleague.tournament;

import java.util.Objects;
import java.util.Set;

/**
 * Technical specification of a robot entered into a competition.
 */
public record RobotSpecification(
        Weight weight,
        Dimensions dimensions,
        RobotHardware hardware
) {
    public RobotSpecification {
        Objects.requireNonNull(weight, "weight cannot be null");
        Objects.requireNonNull(dimensions, "dimensions cannot be null");
        Objects.requireNonNull(hardware, "hardware cannot be null");
    }

    public double weightGrams() {
        return weight.grams();
    }

    public double lengthMm() {
        return dimensions.lengthMm();
    }

    public double widthMm() {
        return dimensions.widthMm();
    }

    public double heightMm() {
        return dimensions.heightMm();
    }

    public int actuatorCount() {
        return hardware.actuatorCount();
    }

    public Set<String> sensors() {
        return hardware.sensors();
    }

    public boolean fitsWithin(RobotLimits limits) {
        Objects.requireNonNull(limits, "limits cannot be null");
        return limits.allows(this);
    }

    public static RobotSpecification of(Weight weight, Dimensions dimensions, RobotHardware hardware) {
        return new RobotSpecification(weight, dimensions, hardware);
    }

    public static RobotSpecification of(double weightGrams, Dimensions dimensions, RobotHardware hardware) {
        return new RobotSpecification(new Weight(weightGrams), dimensions, hardware);
    }

    public static RobotSpecification of(double weightGrams, double lengthMm, double widthMm, double heightMm, int actuatorCount, Set<String> sensors) {
        return new RobotSpecification(
                new Weight(weightGrams),
                new Dimensions(lengthMm, widthMm, heightMm),
                new RobotHardware(actuatorCount, sensors)
        );
    }
}
