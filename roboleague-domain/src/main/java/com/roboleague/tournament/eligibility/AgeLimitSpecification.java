package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamMember;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all members of a team meet the age requirements for the category.
 */
public class AgeLimitSpecification implements EligibilitySpecification<EligibilityCandidate> {
    @Override
    public EligibilityResult isSatisfiedBy(EligibilityCandidate candidate) {
        Team team = candidate.team();
        Category category = candidate.category();
        List<String> violations = new ArrayList<>();

        for (TeamMember member : team.getMembers()) {
            int age = member.getAgeAt(candidate.referenceDate());
            if (age < category.minAge()) {
                violations.add("Member " + member.fullName() + " is under the minimum age limit (" + age + " < " + category.minAge() + ")");
            } else if (category.maxAge() != null && age > category.maxAge()) {
                violations.add("Member " + member.fullName() + " exceeds the maximum age limit (" + age + " > " + category.maxAge() + ")");
            }
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible(violations);
    }
}
