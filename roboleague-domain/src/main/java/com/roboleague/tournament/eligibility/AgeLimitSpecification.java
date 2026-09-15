package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamMember;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates that all members of a team meet the age requirements for the category.
 */
public class AgeLimitSpecification implements EligibilitySpecification<Team> {
    private final LocalDate referenceDate;

    public AgeLimitSpecification(LocalDate referenceDate) {
        this.referenceDate = Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
    }

    public AgeLimitSpecification() {
        this(LocalDate.now());
    }

    @Override
    public EligibilityResult isSatisfiedBy(Team team) {
        Category category = team.getCategory();
        List<String> violations = new ArrayList<>();

        for (TeamMember member : team.getMembers()) {
            int age = member.getAgeAt(referenceDate);
            if (age < category.minAge()) {
                violations.add("Member " + member.fullName() + " is under the minimum age limit (" + age + " < " + category.minAge() + ")");
            } else if (age > category.maxAge()) {
                violations.add("Member " + member.fullName() + " exceeds the maximum age limit (" + age + " > " + category.maxAge() + ")");
            }
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible(violations);
    }
}
