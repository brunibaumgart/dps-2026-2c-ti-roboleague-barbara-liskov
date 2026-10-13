package com.roboleague.tournament.eligibility;

import java.util.Collections;
import java.util.List;

/**
 * Detailed result of an eligibility evaluation.
 */
public record EligibilityResult(
        boolean isEligible,
        List<String> reasons
) {
    public EligibilityResult {
        reasons = reasons != null ? List.copyOf(reasons) : Collections.emptyList();
        if (isEligible && !reasons.isEmpty()) {
            throw new IllegalArgumentException("an eligible result has no reasons against it: " + reasons);
        }
        if (!isEligible && reasons.isEmpty()) {
            throw new IllegalArgumentException("an ineligible result says why");
        }
    }

    public static EligibilityResult eligible() {
        return new EligibilityResult(true, Collections.emptyList());
    }

    public static EligibilityResult ineligible(String reason) {
        return new EligibilityResult(false, List.of(reason));
    }

    public static EligibilityResult ineligible(List<String> reasons) {
        return new EligibilityResult(false, reasons);
    }
}
