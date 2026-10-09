package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class QueryEditionsUseCase {
    private final EditionRepository editions;
    public QueryEditionsUseCase(EditionRepository editions) { this.editions = Objects.requireNonNull(editions); }

    public List<Edition> list() {
        return editions.findAll().stream().sorted(Comparator.comparing(edition -> edition.getId().value())).toList();
    }

    public Edition get(EditionId id) {
        return editions.findById(id).orElseThrow(() -> new IllegalArgumentException("Edition not found: " + id));
    }
}
