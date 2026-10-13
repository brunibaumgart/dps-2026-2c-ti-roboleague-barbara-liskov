package com.roboleague.support;

/**
 * Base of the failures the model reports on purpose, so adapters can tell them apart from programming errors and
 * technical failures. Malformed values keep throwing {@link IllegalArgumentException} from their constructors.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
