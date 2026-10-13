package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.AgeRange;
import com.roboleague.tournament.TeamMember;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all members of a team meet the age requirements for the category, delegating the range to
 * {@link AgeRange} and explaining on which side each member falls out.
 */
public class AgeLimitSpecification implements EligibilitySpecification<EligibilityCandidate> {
    @Override
    public EligibilityResult isSatisfiedBy(EligibilityCandidate candidate) {
        AgeRange range = candidate.category().restrictions().ageRange();
        List<String> violations = new ArrayList<>();

        for (TeamMember member : candidate.team().getMembers()) {
            int age = member.getAgeAt(candidate.referenceDate());
            if (range.contains(age)) {
                continue;
            }
            if (age < range.minAge()) {
                violations.add("Member " + member.fullName() + " is under the minimum age limit (" + age + " < " + range.minAge() + ")");
            } else {
                violations.add("Member " + member.fullName() + " exceeds the maximum age limit (" + age + " > " + range.maxAge() + ")");
            }
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible(violations);
    }
}
