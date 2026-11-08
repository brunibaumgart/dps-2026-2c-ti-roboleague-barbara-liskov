package com.roboleague.repository.jpa;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The one shape every batch read of a JPA adapter has: the distinct keys are queried once (not at all when there
 * are none), each row is rebuilt and the aggregates come back ordered by id text, whatever order Postgres chose.
 */
final class Batches {
    private Batches() {
    }

    static <I, E, T> List<T> load(Collection<I> ids, Function<I, String> key, Function<List<String>, List<E>> query,
                                  Function<E, T> toDomain, Function<T, String> idOf) {
        List<String> keys = ids.stream().distinct().map(key).toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return query.apply(keys).stream().map(toDomain).sorted(Comparator.comparing(idOf)).toList();
    }
}
