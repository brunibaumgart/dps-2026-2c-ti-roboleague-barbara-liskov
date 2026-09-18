package com.roboleague.evaluation;

import java.util.Objects;

public record PolicyInfo(String policyId, String version, String name) {
    public PolicyInfo {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }

    public static PolicyInfo of(String policyId, String version, String name) {
        return new PolicyInfo(policyId, version, name);
    }
}
