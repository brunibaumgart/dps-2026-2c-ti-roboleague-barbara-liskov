package com.roboleague.usecase;

/**
 * A stored aggregate references another one that should exist and does not, such as an attempt whose rulebook
 * version is gone. The request was valid; the stored data is not, so the caller cannot fix it by retrying.
 */
public class InconsistentStateException extends RuntimeException {

    public InconsistentStateException(String message) {
        super(message);
    }
}
