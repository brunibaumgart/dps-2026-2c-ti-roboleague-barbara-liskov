package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Team claim or appeal filed against an attempt result.
 * Managed through the State Pattern (Pending -> UnderReview -> Accepted / Rejected).
 */
public class Appeal {
    private final String appealId;
    private final String attemptId;
    private final String teamId;
    private final String reason;
    private final String evidenceDescription;
    private final LocalDateTime submittedAt;

    private AppealState state;
    private String reviewerId;
    private String resolutionNotes;
    private RawMetrics revisedMetrics;
    private LocalDateTime resolvedAt;

    public Appeal(String appealId, String attemptId, String teamId, String reason, String evidenceDescription) {
        this.appealId = Objects.requireNonNull(appealId, "appealId cannot be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId cannot be null");
        this.teamId = Objects.requireNonNull(teamId, "teamId cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.evidenceDescription = evidenceDescription != null ? evidenceDescription : "";
        this.submittedAt = LocalDateTime.now();
        this.state = new PendingAppealState();
    }

    public String getAppealId() {
        return appealId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidenceDescription() {
        return evidenceDescription;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public AppealState getState() {
        return state;
    }

    public String getStatusName() {
        return state.getStateName();
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public RawMetrics getRevisedMetrics() {
        return revisedMetrics;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    void transitionToState(AppealState newState) {
        this.state = Objects.requireNonNull(newState, "newState cannot be null");
    }

    void setResolution(String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        this.resolutionNotes = resolutionNotes;
        this.revisedMetrics = revisedMetrics;
        this.reviewerId = reviewerId;
    }

    // Business methods delegating to current state
    public void beginReview(String reviewerId) {
        state.beginReview(this, reviewerId);
    }

    public void accept(String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        state.accept(this, resolutionNotes, revisedMetrics, reviewerId);
    }

    public void reject(String resolutionNotes, String reviewerId) {
        state.reject(this, resolutionNotes, reviewerId);
    }

    public boolean isPending() {
        return "PENDING".equals(getStatusName());
    }

    public boolean isAccepted() {
        return "ACCEPTED".equals(getStatusName());
    }

    public boolean isRejected() {
        return "REJECTED".equals(getStatusName());
    }
}
