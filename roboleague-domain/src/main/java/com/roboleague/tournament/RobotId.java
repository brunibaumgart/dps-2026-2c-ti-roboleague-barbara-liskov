package com.roboleague.tournament;

import com.roboleague.support.Identifiers;

/** Textual identity of a robot; preserves the supplied external value. */
public record RobotId(String value) {
    public RobotId {
        Identifiers.validate(value, "robotId");
    }

    public static RobotId of(String value) {
        return new RobotId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
