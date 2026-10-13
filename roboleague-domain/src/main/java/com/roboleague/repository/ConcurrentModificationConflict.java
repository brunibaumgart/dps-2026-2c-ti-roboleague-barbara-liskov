package com.roboleague.repository;

import com.roboleague.support.ConflictException;

/**
 * Thrown by {@code save} when another writer saved the same aggregate first: a stale update or a second creation of
 * the same identity. Nothing of the refused write is stored; loading the aggregate again and retrying is safe.
 */
public class ConcurrentModificationConflict extends ConflictException {

    public ConcurrentModificationConflict(String message, Throwable cause) {
        super(message, cause);
    }
}
