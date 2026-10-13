package com.roboleague.repository.jpa;

import com.roboleague.repository.ConcurrentModificationConflict;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.function.Supplier;

/**
 * Translates what Postgres reports when another writer saved the same aggregate first into the ports'
 * {@link ConcurrentModificationConflict}: a stale version (optimistic lock) or a second insert of the same key.
 * Any other integrity violation is not a concurrent change and propagates unchanged.
 */
final class WriteConflicts {
    private static final String UNIQUE_VIOLATION = "23505";

    private WriteConflicts() { }

    /**
     * Runs a {@code saveAndFlush}, so the conflict surfaces here and not later at commit.
     */
    static <T> T write(Supplier<T> saveAndFlush, String conflictMessage) {
        try {
            return saveAndFlush.get();
        } catch (OptimisticLockingFailureException stale) {
            throw new ConcurrentModificationConflict(conflictMessage, stale);
        } catch (DataIntegrityViolationException violation) {
            if (isDuplicateKey(violation)) {
                throw new ConcurrentModificationConflict(conflictMessage, violation);
            }
            throw violation;
        }
    }

    private static boolean isDuplicateKey(DataIntegrityViolationException violation) {
        if (violation instanceof DuplicateKeyException) {
            return true;
        }
        for (Throwable cause = violation.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint) {
                return constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
                        || UNIQUE_VIOLATION.equals(constraint.getSQLState());
            }
        }
        return false;
    }
}
