package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Loads stored aggregates in a batch by ids that come from other stored aggregates, so one that does not exist is
 * broken data ({@link InconsistentStateException}), not a request for something missing.
 */
final class StoredReferences {
    private StoredReferences() {
    }

    /**
     * Asks {@code find} once for the distinct ids (not at all when there are none) and keys the result by id. Any id
     * it did not return is reported by name.
     */
    static <I, T> Map<I, T> byIds(Collection<I> ids, Function<Collection<I>, List<T>> find, Function<T, I> idOf,
                                  String missingMessage) {
        Objects.requireNonNull(ids, "ids cannot be null");
        Collection<I> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<I, T> found = new LinkedHashMap<>();
        for (T item : find.apply(distinct)) {
            found.put(idOf.apply(item), item);
        }
        List<I> missing = distinct.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new InconsistentStateException(missingMessage + ": " + missing);
        }
        return found;
    }
}
