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
        reasons = reasons != null ? Collections.unmodifiableList(reasons) : Collections.emptyList();
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
