package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.TeamSizeRange;

/**
 * Validates that the team composition matches the minimum and maximum members allowed in the category, delegating
 * the bounds to {@link TeamSizeRange}.
 */
public class TeamSizeSpecification implements EligibilitySpecification<EligibilityCandidate> {

    @Override
    public EligibilityResult isSatisfiedBy(EligibilityCandidate candidate) {
        TeamSizeRange range = candidate.category().restrictions().teamSize();
        int size = candidate.team().getMembers().size();

        if (range.allows(size)) {
            return EligibilityResult.eligible();
        }
        if (size < range.minMembers()) {
            return EligibilityResult.ineligible("Team has fewer members than required: " + size + " < " + range.minMembers());
        }
        return EligibilityResult.ineligible("Team exceeds maximum member count: " + size + " > " + range.maxMembers());
    }
}
