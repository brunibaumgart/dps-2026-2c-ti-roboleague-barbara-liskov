package com.roboleague.tournament;

import java.util.Objects;

/** Textual identity of a robot; preserves the supplied external value. */
public record RobotId(String value) {
    public RobotId {
        Objects.requireNonNull(value, "robotId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("robotId cannot be blank");
        }
    }

    public static RobotId of(String value) {
        return new RobotId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
