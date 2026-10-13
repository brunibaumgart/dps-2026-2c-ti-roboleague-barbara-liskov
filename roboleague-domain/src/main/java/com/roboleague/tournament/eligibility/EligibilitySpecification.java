package com.roboleague.tournament.eligibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification pattern interface for eligibility rules.
 *
 * @param <T> Candidate entity type being verified.
 */
public interface EligibilitySpecification<T> {

    EligibilityResult isSatisfiedBy(T candidate);

    /**
     * Both specifications must hold; when they do not, the reasons of both are kept.
     */
    default EligibilitySpecification<T> and(EligibilitySpecification<T> other) {
        return candidate -> {
            EligibilityResult r1 = this.isSatisfiedBy(candidate);
            EligibilityResult r2 = other.isSatisfiedBy(candidate);
            if (r1.isEligible() && r2.isEligible()) {
                return EligibilityResult.eligible();
            }
            List<String> combined = new ArrayList<>(r1.reasons());
            combined.addAll(r2.reasons());
            return EligibilityResult.ineligible(combined);
        };
    }
}
