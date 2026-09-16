package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object grouping revision identity, author, and timestamp for an audited snapshot.
 */
public record SnapshotMetadata(SnapshotIdentity identity, AuditAuthor author) {
    public SnapshotMetadata {
        Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(author, "author cannot be null");
    }

    public String snapshotId() {
        return identity.snapshotId();
    }

    public int revisionNumber() {
        return identity.revisionNumber();
    }

    public String authorOrJudgeId() {
        return author.authorOrJudgeId();
    }

    public LocalDateTime timestamp() {
        return author.timestamp();
    }

    public static SnapshotMetadata of(SnapshotIdentity identity, AuditAuthor author) {
        return new SnapshotMetadata(identity, author);
    }

    public static SnapshotMetadata of(String snapshotId, int revisionNumber, String authorOrJudgeId) {
        return new SnapshotMetadata(
                new SnapshotIdentity(snapshotId, revisionNumber),
                AuditAuthor.now(authorOrJudgeId)
        );
    }
}
