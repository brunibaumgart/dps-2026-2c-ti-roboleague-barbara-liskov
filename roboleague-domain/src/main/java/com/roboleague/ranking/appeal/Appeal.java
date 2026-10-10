package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.TeamId;

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
    private ActorId reviewerId;
    private String resolutionNotes;
    private RawMetrics revisedMetrics;
    private LocalDateTime resolvedAt;

    public Appeal(AppealTarget target, AppealClaim claim, LocalDateTime submittedAt) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.claim = Objects.requireNonNull(claim, "claim cannot be null");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
        this.state = new PendingAppealState();
    }

    /**
     * Rebuilds a stored appeal as it was, without replaying its transitions.
     * Only persistence mappers use it; business code creates appeals with {@code of} and moves them through their states.
     */
    public static Appeal restore(AppealTarget target, AppealClaim claim, AppealProgress progress) {
        Objects.requireNonNull(progress, "progress cannot be null");
        Appeal appeal = new Appeal(target, claim, progress.submittedAt());
        appeal.state = progress.state();
        appeal.reviewerId = progress.reviewerId();
        appeal.resolutionNotes = progress.resolutionNotes();
        appeal.revisedMetrics = progress.revisedMetrics();
        appeal.resolvedAt = progress.resolvedAt();
        return appeal;
    }

    public AppealTarget getTarget() {
        return target;
    }

    public AppealClaim getClaim() {
        return claim;
    }

    public AppealId getAppealId() {
        return target.appealId();
    }

    public AttemptId getAttemptId() {
        return target.attemptId();
    }

    public TeamId getTeamId() {
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

    public ActorId getReviewerId() {
        return reviewerId;
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

    void transitionToState(AppealState newState) {
        this.state = Objects.requireNonNull(newState, "newState cannot be null");
    }

    /**
     * The reviewer is fixed when the review begins; only the transitions of its states set it.
     */
    void assignReviewer(ActorId reviewerId) {
        this.reviewerId = Objects.requireNonNull(reviewerId, "reviewerId cannot be null");
    }

    /**
     * Records how the review ended. The one who resolves is the reviewer who took the appeal.
     */
    void resolve(String resolutionNotes, RawMetrics revisedMetrics, ActorId resolvedBy, LocalDateTime resolvedAt) {
        if (!this.reviewerId.equals(resolvedBy)) {
            throw new IllegalStateException("Appeal " + getAppealId() + " is under review by " + this.reviewerId
                    + "; " + resolvedBy + " cannot resolve it");
        }
        this.resolutionNotes = resolutionNotes;
        this.revisedMetrics = revisedMetrics;
        this.resolvedAt = resolvedAt;
    }

    public void beginReview(ActorId reviewerId) {
        state.beginReview(this, reviewerId);
    }

    public void accept(String resolutionNotes, RawMetrics revisedMetrics, ActorId reviewerId, LocalDateTime resolvedAt) {
        Objects.requireNonNull(resolvedAt, "resolvedAt cannot be null");
        state.accept(this, resolutionNotes, revisedMetrics, reviewerId, resolvedAt);
    }

    public void reject(String resolutionNotes, ActorId reviewerId, LocalDateTime resolvedAt) {
        Objects.requireNonNull(resolvedAt, "resolvedAt cannot be null");
        state.reject(this, resolutionNotes, reviewerId, resolvedAt);
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

    public static Appeal of(AppealTarget target, AppealClaim claim, LocalDateTime submittedAt) {
        return new Appeal(target, claim, submittedAt);
    }

    public static Appeal of(AppealId appealId, AttemptId attemptId, TeamId teamId, String reason, String evidenceDescription, LocalDateTime submittedAt) {
        return new Appeal(
                new AppealTarget(appealId, attemptId, teamId),
                new AppealClaim(reason, evidenceDescription),
                submittedAt
        );
    }
}
