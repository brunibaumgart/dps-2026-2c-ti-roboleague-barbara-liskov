package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotSpecification;

import java.util.List;

/**
 * GraphQL view of a robot with its specification flattened. Sensors are sorted so the answer is stable.
 */
public record RobotView(String id, String name, double weightGrams, double lengthMm, double widthMm, double heightMm,
                        int actuatorCount, List<String> sensors) {

    public static RobotView from(Robot robot) {
        RobotSpecification spec = robot.getSpecification();
        return new RobotView(robot.getId().value(), robot.getName(), spec.weightGrams(), spec.lengthMm(),
                spec.widthMm(), spec.heightMm(), spec.actuatorCount(), spec.sensors().stream().sorted().toList());
    }
}
