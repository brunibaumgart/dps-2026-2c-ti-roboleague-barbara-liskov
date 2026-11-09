package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.EditionView;
import com.roboleague.port.in.QueryEditions;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Objects;

/**
 * Resolves {@code Query.editions}. Same use case as {@code EditionController}; this adapter only translates.
 */
@Controller
class EditionQueries {

    private final QueryEditions editions;

    EditionQueries(QueryEditions editions) {
        this.editions = Objects.requireNonNull(editions);
    }

    @QueryMapping
    List<EditionView> editions() {
        return editions.list().stream().map(EditionView::from).toList();
    }
}
