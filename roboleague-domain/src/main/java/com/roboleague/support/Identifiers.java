package com.roboleague.support;

import java.util.Objects;

/**
 * Shared validation of the textual identities: present, not blank and no longer than {@link #MAX_LENGTH}, the size
 * every id column has in the database. The text itself is preserved as supplied.
 */
public final class Identifiers {
    public static final int MAX_LENGTH = 64;

    private Identifiers() { }

    public static void validate(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(name + " cannot be longer than " + MAX_LENGTH + " characters");
        }
    }
}
