package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Edition;

import java.util.List;

/**
 * GraphQL view of an edition. {@code challenges} and {@code registrations} are resolved on demand, so they are not
 * components; the categories travel with the edition because they are part of the same aggregate.
 */
public record EditionView(String id, String name, int editionNumber, TournamentView tournament, String startDate,
                          String endDate, List<CategoryView> categories) {

    public static EditionView from(Edition edition) {
        return new EditionView(edition.getId().value(), edition.getName(), edition.getEditionNumber(),
                TournamentView.from(edition.getTournament()), Dates.iso(edition.getStartDate()),
                Dates.iso(edition.getEndDate()), edition.getCategories().stream().map(CategoryView::from).toList());
    }

    /** The category with that id among the ones this edition offers, if it offers it. */
    public java.util.Optional<CategoryView> category(String categoryId) {
        return categories.stream().filter(category -> category.id().equals(categoryId)).findFirst();
    }
}
