package com.roboleague.tournament;

import java.util.Collections;
import java.util.Set;

/**
 * Value object representing the actuator and sensor configuration of a robot.
 */
public record RobotHardware(int actuatorCount, Set<String> sensors) {
    public RobotHardware {
        if (actuatorCount < 0) {
            throw new IllegalArgumentException("actuatorCount cannot be negative");
        }
        sensors = sensors != null ? Collections.unmodifiableSet(sensors) : Collections.emptySet();
    }

    public static RobotHardware of(int actuatorCount, Set<String> sensors) {
        return new RobotHardware(actuatorCount, sensors);
    }
}
