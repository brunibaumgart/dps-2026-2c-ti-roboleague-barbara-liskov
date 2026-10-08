package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.Team;

import java.time.LocalDate;

/** Standard rules shared by enrollment, category changes, updates and scheduling. */
public final class RegistrationEligibility {
    private RegistrationEligibility() {}

    public static EligibilitySpecification<EligibilityCandidate> rules() {
        return new AgeLimitSpecification().and(new TeamSizeSpecification())
                .and(new RobotSpecificationLimit()).and(new DocumentationVerifiedSpecification());
    }

    public static void requireEligible(Team team, Category category, LocalDate referenceDate) {
        EligibilityResult result = rules().isSatisfiedBy(new EligibilityCandidate(team, category, referenceDate));
        if (!result.isEligible()) {
            throw new TeamIneligibleException("Team " + team.getName() + " failed eligibility requirements", result.reasons());
        }
    }
}
