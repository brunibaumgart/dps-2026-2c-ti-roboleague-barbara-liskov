package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Attempt aggregate root in the evaluation bounded context.
 * Implements an append-only audit trail and snapshot revisions.
 */
public class Attempt {

    public enum AttemptStatus {
        PENDING,
        EVALUATED,
        UNDER_APPEAL,
        ADJUSTED,
        DISQUALIFIED
    }

    private final AttemptIdentity identity;
    private final SlotReference slotReference;
    private AttemptStatus status;

    private final List<AttemptScoreSnapshot> revisionHistory;
    private final List<AttemptEvent> eventHistory;

    public Attempt(AttemptIdentity identity, SlotReference slotReference) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        this.slotReference = Objects.requireNonNull(slotReference, "slotReference cannot be null");
        this.status = AttemptStatus.PENDING;
        this.revisionHistory = new ArrayList<>();
        this.eventHistory = new ArrayList<>();
    }

    public AttemptIdentity getIdentity() {
        return identity;
    }

    public SlotReference getSlotReference() {
        return slotReference;
    }

    public String getAttemptId() {
        return identity.attemptId();
    }

    public String getTeamId() {
        return identity.teamId();
    }

    public String getSlotId() {
        return slotReference.slotId();
    }

    public String getRoundId() {
        return slotReference.roundId();
    }

    public int getAttemptNumber() {
        return identity.attemptNumber();
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public List<AttemptScoreSnapshot> getRevisionHistory() {
        return Collections.unmodifiableList(revisionHistory);
    }

    public List<AttemptEvent> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }

    public AttemptScoreSnapshot getLatestSnapshot() {
        if (revisionHistory.isEmpty()) {
            return null;
        }
        return revisionHistory.get(revisionHistory.size() - 1);
    }

    public AttemptScoreSnapshot getOriginalSnapshot() {
        if (revisionHistory.isEmpty()) {
            return null;
        }
        return revisionHistory.get(0);
    }

    public ScoreBreakdown getScoreBreakdown() {
        AttemptScoreSnapshot latest = getLatestSnapshot();
        return latest != null ? latest.breakdown() : ScoreBreakdown.empty();
    }

    public double getFinalScore() {
        ScoreBreakdown breakdown = getScoreBreakdown();
        return breakdown != null ? breakdown.totalScore() : 0.0;
    }

    public RawMetrics getLatestMetrics() {
        AttemptScoreSnapshot latest = getLatestSnapshot();
        return latest != null ? latest.metrics() : null;
    }

    public void registerInitialResult(RawMetrics metrics, ScoreBreakdown breakdown, String judgeId) {
        if (!revisionHistory.isEmpty()) {
            throw new IllegalStateException("Attempt already has registered results. Use adjustment methods instead.");
        }
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(breakdown, "breakdown cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                1,
                judgeId,
                metrics,
                breakdown,
                "Initial attempt result registration"
        );
        revisionHistory.add(snapshot);
        eventHistory.add(ResultRegisteredEvent.create(getAttemptId(), getTeamId(), metrics, breakdown, judgeId));
        this.status = AttemptStatus.EVALUATED;
    }

    public void applyPenaltyAdjustment(int additionalPenalties, String reason, String judgeId, ScoringPolicy policy) {
        if (status == AttemptStatus.PENDING || revisionHistory.isEmpty()) {
            throw new IllegalStateException("Cannot adjust an uncompleted attempt");
        }
        RawMetrics currentMetrics = getLatestMetrics();
        RawMetrics updatedMetrics = RawMetrics.of(
                currentMetrics.timeTakenSeconds(),
                currentMetrics.objectivesCompleted(),
                currentMetrics.penaltiesCount() + additionalPenalties,
                currentMetrics.judgeSubjectiveScores()
        );

        ScoreBreakdown updatedBreakdown = policy.evaluate(updatedMetrics);
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                nextRev,
                judgeId,
                updatedMetrics,
                updatedBreakdown,
                "Penalty applied: " + reason
        );

        revisionHistory.add(snapshot);
        eventHistory.add(PenaltyAppliedEvent.create(getAttemptId(), additionalPenalties, reason, judgeId));
        eventHistory.add(ScoreAdjustedEvent.create(getAttemptId(), nextRev, updatedMetrics, updatedBreakdown, reason, judgeId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void markUnderAppeal() {
        if (revisionHistory.isEmpty()) {
            throw new IllegalStateException("Cannot appeal an attempt without registered results");
        }
        if (status == AttemptStatus.DISQUALIFIED) {
            throw new IllegalStateException("Cannot appeal a disqualified attempt");
        }
        this.status = AttemptStatus.UNDER_APPEAL;
    }

    /**
     * Closes an appeal that was rejected: the score stays untouched and the attempt
     * returns to the status it had before the appeal was filed.
     */
    public void restoreAfterRejectedAppeal() {
        if (status != AttemptStatus.UNDER_APPEAL) {
            throw new IllegalStateException("Attempt is not under appeal");
        }
        this.status = revisionHistory.size() > 1 ? AttemptStatus.ADJUSTED : AttemptStatus.EVALUATED;
    }

    public void adjustAfterAppeal(String appealId, RawMetrics revisedMetrics, ScoreBreakdown revisedBreakdown,
                                  String resolutionNotes, String reviewerId) {
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                nextRev,
                reviewerId,
                revisedMetrics,
                revisedBreakdown,
                "Revision due to accepted appeal " + appealId + ": " + resolutionNotes
        );

        revisionHistory.add(snapshot);
        eventHistory.add(AppealAcceptedEvent.create(getAttemptId(), appealId, resolutionNotes, reviewerId));
        eventHistory.add(ScoreAdjustedEvent.create(getAttemptId(), nextRev, revisedMetrics, revisedBreakdown, resolutionNotes, reviewerId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void recalculateWithPolicy(ScoringPolicy policy, String reason, String authorId) {
        if (revisionHistory.isEmpty()) {
            return;
        }
        RawMetrics currentMetrics = getLatestMetrics();
        ScoreBreakdown recalculated = policy.evaluate(currentMetrics);
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                nextRev,
                authorId,
                currentMetrics,
                recalculated,
                "Recalculation with policy " + policy.version() + ": " + reason
        );

        revisionHistory.add(snapshot);
        eventHistory.add(ScoreAdjustedEvent.create(getAttemptId(), nextRev, currentMetrics, recalculated, reason, authorId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void disqualify(String reason, String judgeId) {
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        if (status == AttemptStatus.DISQUALIFIED) {
            throw new IllegalStateException("Attempt is already disqualified");
        }
        eventHistory.add(AttemptDisqualifiedEvent.create(getAttemptId(), reason, judgeId));
        this.status = AttemptStatus.DISQUALIFIED;
    }

    public static Attempt of(AttemptIdentity identity, SlotReference slotReference) {
        return new Attempt(identity, slotReference);
    }

    public static Attempt of(String attemptId, String teamId, String slotId, String roundId, int attemptNumber) {
        return new Attempt(
                new AttemptIdentity(attemptId, teamId, attemptNumber),
                new SlotReference(slotId, roundId)
        );
    }
}
