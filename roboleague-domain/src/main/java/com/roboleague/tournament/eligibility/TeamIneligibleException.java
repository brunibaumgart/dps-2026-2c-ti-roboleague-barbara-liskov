package com.roboleague.tournament.eligibility;

import com.roboleague.support.BusinessRuleViolation;

import java.util.List;

public class TeamIneligibleException extends BusinessRuleViolation {
    private final List<String> violations;

    public TeamIneligibleException(String message, List<String> violations) {
        super(message + ": " + String.join("; ", violations));
        this.violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public List<String> getViolations() {
        return violations;
    }
}
