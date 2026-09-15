package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.*;

import java.time.LocalDateTime;
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

    private final String attemptId;
    private final String teamId;
    private final String slotId;
    private final String roundId;
    private final int attemptNumber;
    private AttemptStatus status;

    private final List<AttemptScoreSnapshot> revisionHistory;
    private final List<AttemptEvent> eventHistory;

    public Attempt(String attemptId, String teamId, String slotId, String roundId, int attemptNumber) {
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId cannot be null");
        this.teamId = Objects.requireNonNull(teamId, "teamId cannot be null");
        this.slotId = Objects.requireNonNull(slotId, "slotId cannot be null");
        this.roundId = Objects.requireNonNull(roundId, "roundId cannot be null");
        this.attemptNumber = attemptNumber;
        this.status = AttemptStatus.PENDING;
        this.revisionHistory = new ArrayList<>();
        this.eventHistory = new ArrayList<>();
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getRoundId() {
        return roundId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
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

        AttemptScoreSnapshot snapshot = new AttemptScoreSnapshot(
                UUID.randomUUID().toString(),
                1,
                LocalDateTime.now(),
                judgeId,
                metrics,
                breakdown,
                "Initial attempt result registration"
        );
        revisionHistory.add(snapshot);
        eventHistory.add(ResultRegisteredEvent.create(attemptId, teamId, metrics, breakdown, judgeId));
        this.status = AttemptStatus.EVALUATED;
    }

    public void applyPenaltyAdjustment(int additionalPenalties, String reason, String judgeId, ScoringPolicy policy) {
        if (status == AttemptStatus.PENDING || revisionHistory.isEmpty()) {
            throw new IllegalStateException("Cannot adjust an uncompleted attempt");
        }
        RawMetrics currentMetrics = getLatestMetrics();
        RawMetrics updatedMetrics = new RawMetrics(
                currentMetrics.timeTakenSeconds(),
                currentMetrics.objectivesCompleted(),
                currentMetrics.penaltiesCount() + additionalPenalties,
                currentMetrics.resourceConsumption(),
                currentMetrics.judgeSubjectiveScores(),
                currentMetrics.customMetrics()
        );

        ScoreBreakdown updatedBreakdown = policy.evaluate(updatedMetrics);
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = new AttemptScoreSnapshot(
                UUID.randomUUID().toString(),
                nextRev,
                LocalDateTime.now(),
                judgeId,
                updatedMetrics,
                updatedBreakdown,
                "Penalty applied: " + reason
        );

        revisionHistory.add(snapshot);
        eventHistory.add(PenaltyAppliedEvent.create(attemptId, additionalPenalties, reason, judgeId));
        eventHistory.add(ScoreAdjustedEvent.create(attemptId, nextRev, updatedMetrics, updatedBreakdown, reason, judgeId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void markUnderAppeal() {
        this.status = AttemptStatus.UNDER_APPEAL;
    }

    public void adjustAfterAppeal(String appealId, RawMetrics revisedMetrics, ScoreBreakdown revisedBreakdown,
                                  String resolutionNotes, String reviewerId) {
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = new AttemptScoreSnapshot(
                UUID.randomUUID().toString(),
                nextRev,
                LocalDateTime.now(),
                reviewerId,
                revisedMetrics,
                revisedBreakdown,
                "Revision due to accepted appeal " + appealId + ": " + resolutionNotes
        );

        revisionHistory.add(snapshot);
        eventHistory.add(AppealAcceptedEvent.create(attemptId, appealId, resolutionNotes, reviewerId));
        eventHistory.add(ScoreAdjustedEvent.create(attemptId, nextRev, revisedMetrics, revisedBreakdown, resolutionNotes, reviewerId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void recalculateWithPolicy(ScoringPolicy policy, String reason, String authorId) {
        if (revisionHistory.isEmpty()) {
            return;
        }
        RawMetrics currentMetrics = getLatestMetrics();
        ScoreBreakdown recalculated = policy.evaluate(currentMetrics);
        int nextRev = revisionHistory.size() + 1;

        AttemptScoreSnapshot snapshot = new AttemptScoreSnapshot(
                UUID.randomUUID().toString(),
                nextRev,
                LocalDateTime.now(),
                authorId,
                currentMetrics,
                recalculated,
                "Recalculation with policy " + policy.version() + ": " + reason
        );

        revisionHistory.add(snapshot);
        eventHistory.add(ScoreAdjustedEvent.create(attemptId, nextRev, currentMetrics, recalculated, reason, authorId));
        this.status = AttemptStatus.ADJUSTED;
    }

    public void disqualify(String reason, String judgeId) {
        this.status = AttemptStatus.DISQUALIFIED;
    }
}
