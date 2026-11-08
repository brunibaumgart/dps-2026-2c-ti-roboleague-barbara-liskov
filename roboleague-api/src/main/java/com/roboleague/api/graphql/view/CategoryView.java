package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryRestrictions;
import com.roboleague.tournament.Dimensions;

/**
 * GraphQL view of a category with its eligibility limits, flattened like the REST {@code CategoryDto}.
 * {@code maxAge} is null when the category has no upper age limit.
 */
public record CategoryView(String id, String name, int minMembers, int maxMembers, int minAge, Integer maxAge,
                           double maxWeightGrams, double maxLengthMm, double maxWidthMm, double maxHeightMm) {

    public static CategoryView from(Category category) {
        CategoryRestrictions limits = category.restrictions();
        Dimensions robot = limits.robotLimits().maxDimensions();
        return new CategoryView(category.id().value(), category.name(), limits.teamSize().minMembers(),
                limits.teamSize().maxMembers(), limits.ageRange().minAge(), limits.ageRange().maxAge(),
                limits.robotLimits().maxWeight().grams(), robot.lengthMm(), robot.widthMm(), robot.heightMm());
    }
}
