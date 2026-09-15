package com.roboleague.tournament;

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
    private String verifiedBy;
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

    public void verify(String verifiedBy) {
        this.verified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verifiedBy = Objects.requireNonNull(verifiedBy, "verifiedBy cannot be null");
    }

    public void revokeVerification(String reason) {
        this.verified = false;
        this.verifiedAt = null;
        this.verifiedBy = null;
    }

    public boolean isVerified() {
        return verified;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Map<String, String> getDocuments() {
        return Collections.unmodifiableMap(documents);
    }
}
