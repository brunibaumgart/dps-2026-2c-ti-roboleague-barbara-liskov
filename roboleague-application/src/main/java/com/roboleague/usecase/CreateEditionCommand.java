package com.roboleague.usecase;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.EditionContext;

import java.util.List;
import java.util.Objects;

public record CreateEditionCommand(EditionContext context, DateRange dates, List<Category> categories) {
    public CreateEditionCommand {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(dates, "dates cannot be null");
        categories = List.copyOf(Objects.requireNonNull(categories, "categories cannot be null"));
    }
}
