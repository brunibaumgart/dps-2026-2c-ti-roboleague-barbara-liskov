package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;

import java.util.ArrayList;
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
        List<String> violations = new ArrayList<>();

        if (spec.weightGrams() > category.maxRobotWeightGrams()) {
            violations.add("Robot weight (" + spec.weightGrams() + "g) exceeds category limit (" + category.maxRobotWeightGrams() + "g)");
        }
        if (spec.lengthMm() > category.maxRobotLengthMm()) {
            violations.add("Robot length (" + spec.lengthMm() + "mm) exceeds category limit (" + category.maxRobotLengthMm() + "mm)");
        }
        if (spec.widthMm() > category.maxRobotWidthMm()) {
            violations.add("Robot width (" + spec.widthMm() + "mm) exceeds category limit (" + category.maxRobotWidthMm() + "mm)");
        }
        if (spec.heightMm() > category.maxRobotHeightMm()) {
            violations.add("Robot height (" + spec.heightMm() + "mm) exceeds category limit (" + category.maxRobotHeightMm() + "mm)");
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible();
        }
        return EligibilityResult.ineligible(violations);
    }
}
