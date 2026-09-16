package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;

import java.util.List;

/**
 * Validates that the team's robot meets weight and dimensional limits for the category.
 */
public class RobotSpecificationLimit implements EligibilitySpecification<Team> {

    @Override
    public EligibilityResult isSatisfiedBy(Team team) {
        if (team.getRobot() == null) {
            return EligibilityResult.ineligible("Team does not have an assigned robot");
        }

        Category category = team.getCategory();
        RobotSpecification spec = team.getRobot().getSpecification();

        List<String> violations = category.restrictions().robotLimits().checkViolations(spec);

        if (violations.isEmpty()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible(violations);
    }
}
