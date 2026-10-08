package com.roboleague.tournament;

import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Team documentation status and verified records.
 */
public class Documentation {
    private boolean verified;
    private LocalDateTime verifiedAt;
    private ActorId verifiedBy;
    private String revocationReason;
    private final Map<String, String> documents;

    public Documentation() {
        this.verified = false;
        this.documents = new HashMap<>();
    }

    public void addDocument(String documentType, String fileReference) {
        Objects.requireNonNull(documentType, "documentType cannot be null");
        Objects.requireNonNull(fileReference, "fileReference cannot be null");
        this.documents.put(documentType, fileReference);
    }

    public void verify(ActorId verifiedBy, LocalDateTime verifiedAt) {
        Objects.requireNonNull(verifiedAt, "verifiedAt cannot be null");
        Objects.requireNonNull(verifiedBy, "verifiedBy cannot be null");
        this.verified = true;
        this.verifiedAt = verifiedAt;
        this.verifiedBy = verifiedBy;
        this.revocationReason = null;
    }

    public void revokeVerification(String reason) {
        this.verified = false;
        this.verifiedAt = null;
        this.verifiedBy = null;
        this.revocationReason = reason != null ? reason : "Revoked without stated reason";
    }

    public boolean isVerified() {
        return verified;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public ActorId getVerifiedBy() {
        return verifiedBy;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public Map<String, String> getDocuments() {
        return Collections.unmodifiableMap(documents);
    }
}
