package com.roboleague.tournament;

import java.util.Objects;

/**
 * Robot entity representing the hardware competing for a team.
 */
public final class Robot {
    private final RobotId id;
    private final String name;
    private final RobotSpecification specification;

    public Robot(RobotId id, String name, RobotSpecification specification) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.specification = Objects.requireNonNull(specification, "specification cannot be null");
    }

    public RobotId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RobotSpecification getSpecification() {
        return specification;
    }

    public Robot withSpecification(RobotSpecification specification) {
        return new Robot(id, name, specification);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Robot robot)) return false;
        return Objects.equals(id, robot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
