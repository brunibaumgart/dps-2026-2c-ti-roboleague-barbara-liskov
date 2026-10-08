package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Team;

/**
 * Validates that team documentation has been submitted and formally verified.
 */
public class DocumentationVerifiedSpecification implements EligibilitySpecification<EligibilityCandidate> {

    @Override
    public EligibilityResult isSatisfiedBy(EligibilityCandidate candidate) {
        Team team = candidate.team();
        if (team.getDocumentation() != null && team.getDocumentation().isVerified()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible("Team documentation has not been verified");
    }
}
