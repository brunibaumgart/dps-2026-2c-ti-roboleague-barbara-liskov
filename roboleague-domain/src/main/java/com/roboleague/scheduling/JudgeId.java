package com.roboleague.scheduling;

import com.roboleague.support.ActorId;
import com.roboleague.support.Identifiers;

/** Textual identity of a judge; preserves the supplied external value. */
public record JudgeId(String value) {
    public JudgeId {
        Identifiers.validate(value, "judgeId");
    }

    public static JudgeId of(String value) {
        return new JudgeId(value);
    }

    /** Explicitly identifies this judge as the author of an audit operation. */
    public ActorId asActorId() {
        return ActorId.of(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
