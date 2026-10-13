package com.roboleague.support;

/**
 * A request that clashes with the current state of the data, such as resolving an appeal someone else is reviewing
 * or saving over a change made in between. Loading the current state again shows why.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
