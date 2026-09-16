package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Team claim or appeal filed against an attempt result.
 * Managed through the State Pattern (Pending -> UnderReview -> Accepted / Rejected).
 */
public class Appeal {
    private final AppealTarget target;
    private final AppealClaim claim;
    private final LocalDateTime submittedAt;

    private AppealState state;
    private String reviewerId;
    private String resolutionNotes;
    private RawMetrics revisedMetrics;
    private LocalDateTime resolvedAt;

    public Appeal(AppealTarget target, AppealClaim claim) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.claim = Objects.requireNonNull(claim, "claim cannot be null");
        this.submittedAt = LocalDateTime.now();
        this.state = new PendingAppealState();
    }

    public AppealTarget getTarget() {
        return target;
    }

    public AppealClaim getClaim() {
        return claim;
    }

    public String getAppealId() {
        return target.appealId();
    }

    public String getAttemptId() {
        return target.attemptId();
    }

    public String getTeamId() {
        return target.teamId();
    }

    public String getReason() {
        return claim.reason();
    }

    public String getEvidenceDescription() {
        return claim.evidenceDescription();
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
        return state.isPending();
    }

    public boolean isUnderReview() {
        return state.isUnderReview();
    }

    public boolean isAccepted() {
        return state.isAccepted();
    }

    public boolean isRejected() {
        return state.isRejected();
    }

    public boolean isResolved() {
        return state.isResolved();
    }

    public boolean canPublishOfficialRanking() {
        return state.canPublishOfficialRanking();
    }

    public static Appeal of(AppealTarget target, AppealClaim claim) {
        return new Appeal(target, claim);
    }

    public static Appeal of(String appealId, String attemptId, String teamId, String reason, String evidenceDescription) {
        return new Appeal(
                new AppealTarget(appealId, attemptId, teamId),
                new AppealClaim(reason, evidenceDescription)
        );
    }
}
