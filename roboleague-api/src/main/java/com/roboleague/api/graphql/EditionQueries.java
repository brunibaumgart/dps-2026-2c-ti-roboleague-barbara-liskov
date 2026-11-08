package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.ChallengeView;
import com.roboleague.api.graphql.view.EditionView;
import com.roboleague.api.graphql.view.RegistrationView;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.ListEditionChallenges;
import com.roboleague.port.in.QueryEditions;
import com.roboleague.port.in.QueryRegistrations;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Objects;

/** Reads editions and their relations through the same input ports as REST. */
@Controller
class EditionQueries {

    private final QueryEditions editions;
    private final ListEditionChallenges challenges;
    private final QueryRegistrations registrations;

    EditionQueries(QueryEditions editions, ListEditionChallenges challenges, QueryRegistrations registrations) {
        this.editions = Objects.requireNonNull(editions);
        this.challenges = Objects.requireNonNull(challenges);
        this.registrations = Objects.requireNonNull(registrations);
    }

    @QueryMapping
    List<EditionView> editions() {
        return editions.list().stream().map(EditionView::from).toList();
    }

    @QueryMapping
    EditionView edition(@Argument String id) {
        return EditionView.from(editions.get(EditionId.of(id)));
    }

    @SchemaMapping(typeName = "Edition", field = "challenges")
    List<ChallengeView> challenges(EditionView edition) {
        return challenges.execute(EditionId.of(edition.id())).stream().map(ChallengeView::from).toList();
    }

    @SchemaMapping(typeName = "Edition", field = "registrations")
    List<RegistrationView> registrations(EditionView edition, @Argument String categoryId) {
        if (categoryId != null) {
            CategoryId category = CategoryId.of(categoryId);
            if (edition.category(category.value()).isEmpty()) {
                throw new IllegalArgumentException("Category " + category + " is not offered in edition " + edition.id());
            }
        }
        return registrations.list(EditionId.of(edition.id())).stream()
                .map(registered -> registered.registration())
                .filter(registration -> categoryId == null || registration.categoryId().value().equals(categoryId))
                .map(registration -> RegistrationView.from(registration,
                        edition.category(registration.categoryId().value()).orElseThrow(() ->
                                new InconsistentStateException("Registered category not found: " + registration.categoryId()))))
                .toList();
    }
}
