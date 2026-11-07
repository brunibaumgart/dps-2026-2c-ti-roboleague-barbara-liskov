package com.roboleague.repository.memory;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The one shape every batch read of an in-memory adapter has: each distinct id is looked up as {@code findById}
 * does (so adapters that hand out copies keep doing so), ids that do not exist are skipped and the aggregates come
 * back ordered by id text, as the JPA adapters return them.
 */
final class Batches {
    private Batches() {
    }

    static <I, T> List<T> find(Collection<I> ids, Function<I, Optional<T>> lookup, Function<T, String> idOf) {
        return ids.stream().distinct().map(lookup).flatMap(Optional::stream)
                .sorted(Comparator.comparing(idOf)).toList();
    }
}
