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
    private AttemptStatus status;

    private final List<AttemptScoreSnapshot> revisionHistory;
    private final List<AttemptEvent> eventHistory;

    public Attempt(AttemptIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        this.status = AttemptStatus.PENDING;
        this.revisionHistory = new ArrayList<>();
        this.eventHistory = new ArrayList<>();
    }

    public AttemptIdentity getIdentity() {
        return identity;
    }

    public AttemptId getId() {
        return identity.id();
    }

    public String getTeamId() {
        return identity.teamId();
    }

    public String getSlotId() {
        return identity.id().slotId();
    }

    public String getRoundId() {
        return identity.roundId();
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

    /**
     * Scores what was captured with the rulebook. The attempt computes its own score: nobody hands it one.
     */
    public void registerInitialResult(RawMetrics metrics, String judgeId, Rulebook rulebook) {
        if (!revisionHistory.isEmpty()) {
            throw new IllegalStateException("Attempt already has registered results. Use adjustment methods instead.");
        }
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
        ScoreBreakdown breakdown = rulebook.evaluate(metrics);

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                1,
                judgeId,
                metrics,
                breakdown,
                "Initial attempt result registration"
        );
        revisionHistory.add(snapshot);
        eventHistory.add(ResultRegisteredEvent.create(getId().value(), getTeamId(), metrics, breakdown, judgeId));
        this.status = AttemptStatus.EVALUATED;
    }

    public void applyPenaltyAdjustment(int additionalPenalties, AuditNote note, Rulebook rulebook) {
        String reason = note.reason();
        String judgeId = note.authorId();
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

        ScoreBreakdown updatedBreakdown = rulebook.evaluate(updatedMetrics);
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
        eventHistory.add(PenaltyAppliedEvent.create(getId().value(), additionalPenalties, reason, judgeId));
        eventHistory.add(ScoreAdjustedEvent.create(getId().value(), nextRev, updatedMetrics, updatedBreakdown, reason, judgeId));
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

    public void restoreAfterRejectedAppeal() {
        if (status != AttemptStatus.UNDER_APPEAL) {
            throw new IllegalStateException("Attempt is not under appeal");
        }
        this.status = revisionHistory.size() > 1 ? AttemptStatus.ADJUSTED : AttemptStatus.EVALUATED;
    }

    /**
     * Adds a revision with the metrics an accepted appeal corrected, scored with the rulebook.
     */
    public void adjustAfterAppeal(AppealRevision revision, Rulebook rulebook) {
        String appealId = revision.appealId();
        RawMetrics revisedMetrics = revision.metrics();
        String resolutionNotes = revision.note().reason();
        String reviewerId = revision.note().authorId();
        ScoreBreakdown revisedBreakdown = rulebook.evaluate(revisedMetrics);
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
        eventHistory.add(AppealAcceptedEvent.create(getId().value(), appealId, resolutionNotes, reviewerId));
        eventHistory.add(ScoreAdjustedEvent.create(getId().value(), nextRev, revisedMetrics, revisedBreakdown, resolutionNotes, reviewerId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void recalculateWith(Rulebook rulebook, String reason, String authorId) {
        if (revisionHistory.isEmpty()) {
            return;
        }
        RawMetrics currentMetrics = getLatestMetrics();
        ScoreBreakdown recalculated = rulebook.evaluate(currentMetrics);
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = AttemptScoreSnapshot.of(
                UUID.randomUUID().toString(),
                nextRev,
                authorId,
                currentMetrics,
                recalculated,
                "Recalculation with rulebook " + rulebook.version() + ": " + reason
        );

        revisionHistory.add(snapshot);
        eventHistory.add(ScoreAdjustedEvent.create(getId().value(), nextRev, currentMetrics, recalculated, reason, authorId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void disqualify(String reason, String judgeId) {
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        if (status == AttemptStatus.DISQUALIFIED) {
            throw new IllegalStateException("Attempt is already disqualified");
        }
        eventHistory.add(AttemptDisqualifiedEvent.create(getId().value(), reason, judgeId));
        this.status = AttemptStatus.DISQUALIFIED;
    }

    public static Attempt of(AttemptIdentity identity) {
        return new Attempt(identity);
    }
}
