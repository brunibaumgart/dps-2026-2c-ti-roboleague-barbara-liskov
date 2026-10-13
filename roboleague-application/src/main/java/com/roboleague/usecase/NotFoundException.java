package com.roboleague.usecase;

import java.util.Objects;

/**
 * The resource a request names by its id does not exist. Only for ids the caller supplied: a missing reference
 * between stored aggregates is an {@link InconsistentStateException}.
 */
public class NotFoundException extends RuntimeException {
    private final String resource;
    private final String id;

    public NotFoundException(String resource, Object id) {
        this(resource, id, resource + " not found: " + id);
    }

    /** With a message that says more than "not found", such as standings that were never calculated. */
    public NotFoundException(String resource, Object id, String message) {
        super(message);
        this.resource = Objects.requireNonNull(resource, "resource cannot be null");
        this.id = String.valueOf(id);
    }

    public String resource() {
        return resource;
    }

    public String id() {
        return id;
    }
}
