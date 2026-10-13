package com.roboleague.repository.jpa;

import java.util.function.Supplier;

/**
 * A stored row that cannot be rebuilt into its aggregate: an unknown enum constant or event type, a rulebook the
 * catalog refuses, or values the domain rejects. It is a failure of the stored data, not of the request that read
 * it, so the original cause stays chained for the logs.
 */
public class CorruptDataException extends RuntimeException {

    public CorruptDataException(String message) {
        super(message);
    }

    public CorruptDataException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Runs a mapper's rebuild and turns whatever the domain or the JSON refuses into a {@link CorruptDataException}.
     */
    static <T> T rebuilding(String stored, Supplier<T> rebuild) {
        try {
            return rebuild.get();
        } catch (CorruptDataException corrupt) {
            throw corrupt;
        } catch (RuntimeException invalid) {
            throw new CorruptDataException(stored + " cannot be rebuilt: " + invalid.getMessage(), invalid);
        }
    }
}
