package com.roboleague.api;

/** Presence/format checks at the HTTP boundary, before constructing domain values. */
public final class RequestValues {
    private RequestValues() { }

    public static <T> T required(T value, String field) {
        if (value == null) { throw new IllegalArgumentException("Missing field '" + field + "'"); }
        return value;
    }

    public static String text(String value, String field) {
        if (required(value, field).isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' cannot be blank");
        }
        return value;
    }
}
