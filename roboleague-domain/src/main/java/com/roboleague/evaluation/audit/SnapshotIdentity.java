package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Value object representing identification and revision number of a snapshot.
 */
public record SnapshotIdentity(String snapshotId, int revisionNumber) {
    public SnapshotIdentity {
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        if (revisionNumber <= 0) {
            throw new IllegalArgumentException("revisionNumber must be positive");
        }
    }

    public static SnapshotIdentity of(String snapshotId, int revisionNumber) {
        return new SnapshotIdentity(snapshotId, revisionNumber);
    }
}
