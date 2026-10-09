package com.roboleague.evaluation;

import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * The rulebook an attempt is scored with: its challenge and the version that was current when the attempt was
 * captured. Every revision of the attempt uses that same version, even after the challenge publishes a new one.
 */
public record RulebookReference(ChallengeId challengeId, RulebookVersion version) {
    public RulebookReference {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
    }

    public static RulebookReference of(ChallengeId challengeId, Rulebook rulebook) {
        return new RulebookReference(challengeId, rulebook.version());
    }

    void requireMatch(Rulebook rulebook) {
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
        if (!version.equals(rulebook.version())) {
            throw new IllegalArgumentException("the attempt is scored with " + this + ", not with "
                    + rulebook.version());
        }
    }

    @Override
    public String toString() {
        return challengeId + " " + version;
    }
}
