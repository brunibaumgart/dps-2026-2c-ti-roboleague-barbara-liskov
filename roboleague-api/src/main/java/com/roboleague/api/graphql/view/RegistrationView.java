package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Registration;

/**
 * GraphQL view of a registration. {@code category} comes from the edition already loaded (no query);
 * {@code team} is resolved in batch, so the view keeps only {@code teamId}.
 */
public record RegistrationView(String editionId, String categoryId, CategoryView category, String registeredAt,
                               String referenceDate, String teamId) {

    public static RegistrationView from(Registration registration, CategoryView category) {
        return new RegistrationView(registration.editionId().value(), registration.categoryId().value(), category,
                Dates.iso(registration.registeredAt()), Dates.iso(registration.referenceDate()),
                registration.teamId().value());
    }
}
