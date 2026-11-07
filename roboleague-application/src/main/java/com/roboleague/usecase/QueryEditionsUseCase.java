package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QueryEditionsUseCase {
    private final EditionRepository editions;
    public QueryEditionsUseCase(EditionRepository editions) { this.editions = Objects.requireNonNull(editions); }

    public List<Edition> list() {
        return editions.findAll().stream().sorted(Comparator.comparing(edition -> edition.getId().value())).toList();
    }

    public Edition get(EditionId id) {
        return editions.findById(id).orElseThrow(() -> new NotFoundException("Edition", id));
    }

    /**
     * The editions with those ids, keyed by id, in one read however many ids there are (none for an empty
     * collection). A stored reference that does not exist is an InconsistentStateException.
     */
    public Map<EditionId, Edition> byIds(Collection<EditionId> ids) {
        return StoredReferences.byIds(ids, editions::findByIds, Edition::getId, "Stored edition not found");
    }
}
