package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.Team;

/**
 * Validates that the team composition matches the minimum and maximum members allowed in the category.
 */
public class TeamSizeSpecification implements EligibilitySpecification<Team> {

    @Override
    public EligibilityResult isSatisfiedBy(Team team) {
        Category category = team.getCategory();
        int size = team.getMembers().size();

        if (size < category.minTeamMembers()) {
            return EligibilityResult.ineligible("Team has fewer members than required: " + size + " < " + category.minTeamMembers());
        }
        if (size > category.maxTeamMembers()) {
            return EligibilityResult.ineligible("Team exceeds maximum member count: " + size + " > " + category.maxTeamMembers());
        }
        return EligibilityResult.eligible();
    }
}
