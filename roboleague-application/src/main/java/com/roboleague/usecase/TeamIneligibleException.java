package com.roboleague.usecase;

import java.util.List;

public class TeamIneligibleException extends RuntimeException {
    private final List<String> violations;

    public TeamIneligibleException(String message, List<String> violations) {
        super(message + ": " + String.join("; ", violations));
        this.violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public List<String> getViolations() {
        return violations;
    }
}
