package com.roboleague.ranking.appeal;

import java.util.Objects;

/**
 * Value object detailing the grievance and supporting evidence of an appeal.
 */
public record AppealClaim(String reason, String evidenceDescription) {
    public AppealClaim {
        Objects.requireNonNull(reason, "reason cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("an appeal says what it claims: reason cannot be blank");
        }
        evidenceDescription = evidenceDescription != null ? evidenceDescription : "";
    }

    public static AppealClaim of(String reason, String evidenceDescription) {
        return new AppealClaim(reason, evidenceDescription);
    }
}
