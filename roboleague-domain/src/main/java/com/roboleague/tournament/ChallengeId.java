package com.roboleague.tournament;

import com.roboleague.support.Identifiers;

public record ChallengeId(String value) {
    public ChallengeId {
        Identifiers.validate(value, "challengeId");
    }

    public static ChallengeId of(String value) {
        return new ChallengeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
