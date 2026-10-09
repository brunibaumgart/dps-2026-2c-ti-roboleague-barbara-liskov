package com.roboleague.tournament;

import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable documents and their verification state. */
public final class Documentation {
    private final boolean verified;
    private final LocalDateTime verifiedAt;
    private final ActorId verifiedBy;
    private final String revocationReason;
    private final Map<String, String> documents;

    public Documentation() {
        this(Map.of(), false, null, null, null);
    }

    private Documentation(Map<String, String> documents, boolean verified, LocalDateTime verifiedAt,
                          ActorId verifiedBy, String revocationReason) {
        this.documents = Map.copyOf(documents);
        if (verified) {
            Objects.requireNonNull(verifiedAt, "verifiedAt cannot be null");
            Objects.requireNonNull(verifiedBy, "verifiedBy cannot be null");
            if (revocationReason != null) throw new IllegalArgumentException("Verified documents cannot have a revocation reason");
        } else if (verifiedAt != null || verifiedBy != null) {
            throw new IllegalArgumentException("Unverified documents cannot have verification metadata");
        }
        this.verified = verified;
        this.verifiedAt = verifiedAt;
        this.verifiedBy = verifiedBy;
        this.revocationReason = revocationReason;
    }

    /** A changed document set must be verified again. */
    public Documentation withDocument(String documentType, String fileReference) {
        Objects.requireNonNull(documentType, "documentType cannot be null");
        Objects.requireNonNull(fileReference, "fileReference cannot be null");
        var changed = new HashMap<>(documents);
        changed.put(documentType, fileReference);
        return new Documentation(changed, false, null, null, null);
    }

    public Documentation verify(ActorId verifiedBy, LocalDateTime verifiedAt) {
        return new Documentation(documents, true, verifiedAt, verifiedBy, null);
    }

    public Documentation revokeVerification(String reason) {
        return new Documentation(documents, false, null, null,
                reason != null ? reason : "Revoked without stated reason");
    }

    public boolean isVerified() { return verified; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public ActorId getVerifiedBy() { return verifiedBy; }
    public String getRevocationReason() { return revocationReason; }
    public Map<String, String> getDocuments() { return documents; }

    /** Rehydrates verification facts without repeating a business operation. */
    public static Documentation restore(Map<String, String> documents, boolean verified, LocalDateTime verifiedAt,
                                        ActorId verifiedBy, String revocationReason) {
        return new Documentation(documents, verified, verifiedAt, verifiedBy, revocationReason);
    }
}
