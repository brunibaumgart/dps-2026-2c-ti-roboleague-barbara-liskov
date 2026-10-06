package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Attempt aggregate root in the evaluation bounded context.
 * Implements an append-only audit trail and snapshot revisions. It is scored with the rulebook version that was
 * current when it was captured, and every revision records that version.
 */
public class Attempt {

    public enum AttemptStatus {
        SCHEDULED("scheduled"),
        EVALUATED("evaluated"),
        UNDER_APPEAL("under appeal"),
        ADJUSTED("adjusted"),
        DISQUALIFIED("disqualified");

        private final String label;

        AttemptStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final AttemptIdentity identity;
    private final RulebookReference rulebook;
    private AttemptState state;

    private final List<AttemptScoreSnapshot> revisionHistory;
    private final List<AttemptEvent> eventHistory;

    public Attempt(AttemptIdentity identity, RulebookReference rulebook) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        this.rulebook = Objects.requireNonNull(rulebook, "rulebook cannot be null");
        this.state = new ScheduledAttemptState();
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

    /**
     * The challenge and rulebook version this attempt is scored with.
     */
    public RulebookReference getRulebookReference() {
        return rulebook;
    }

    public AttemptStatus getStatus() {
        return state.status();
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
        AttemptState next = state.scored();
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        EvaluationSnapshot evaluation = scored(metrics, rulebook);

        addRevision(evaluation, judgeId, "Initial attempt result registration");
        eventHistory.add(ResultRegisteredEvent.create(getId().value(), getTeamId(), evaluation, judgeId));
        this.state = next;
    }

    public void applyPenaltyAdjustment(int additionalPenalties, AuditNote note, Rulebook rulebook) {
        AttemptState next = state.faultsAdjusted();
        String reason = note.reason();
        String judgeId = note.authorId();
        RawMetrics currentMetrics = getLatestMetrics();
        RawMetrics updatedMetrics = currentMetrics.withPenalties(currentMetrics.penaltiesCount() + additionalPenalties);

        EvaluationSnapshot evaluation = scored(updatedMetrics, rulebook);

        int revision = addRevision(evaluation, judgeId, "Penalty applied: " + reason);
        eventHistory.add(PenaltyAppliedEvent.create(getId().value(), additionalPenalties, reason, judgeId));
        eventHistory.add(ScoreAdjustedEvent.create(getId().value(), revision, evaluation, note));
        this.state = next;
    }

    /**
     * Opens one more appeal on the attempt; it stays under appeal until every open appeal is closed.
     */
    public void markUnderAppeal() {
        this.state = state.appealFiled();
    }

    public void restoreAfterRejectedAppeal() {
        this.state = state.appealRejected();
    }

    /**
     * Adds a revision with the metrics an accepted appeal corrected, scored with the rulebook.
     */
    public void adjustAfterAppeal(AppealRevision revision, Rulebook rulebook) {
        AttemptState next = state.appealAccepted();
        String appealId = revision.appealId();
        AuditNote note = revision.note();
        EvaluationSnapshot evaluation = scored(revision.metrics(), rulebook);

        int number = addRevision(evaluation, note.authorId(),
                "Revision due to accepted appeal " + appealId + ": " + note.reason());
        eventHistory.add(AppealAcceptedEvent.create(getId().value(), appealId, note.reason(), note.authorId()));
        eventHistory.add(ScoreAdjustedEvent.create(getId().value(), number, evaluation, note));
        this.state = next;
    }

    public void disqualify(String reason, String judgeId) {
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        AttemptState next = state.disqualified();
        eventHistory.add(AttemptDisqualifiedEvent.create(getId().value(), reason, judgeId));
        this.state = next;
    }

    /**
     * Scores the metrics with the attempt's own rulebook version; any other version is a mistake of the caller.
     */
    private EvaluationSnapshot scored(RawMetrics metrics, Rulebook rulebook) {
        this.rulebook.requireMatch(rulebook);
        return EvaluationSnapshot.scoring(metrics, rulebook);
    }

    private int addRevision(EvaluationSnapshot evaluation, String authorId, String reason) {
        int number = revisionHistory.size() + 1;
        SnapshotMetadata metadata = SnapshotMetadata.of(getId().value() + "-r" + number, number, authorId);
        revisionHistory.add(AttemptScoreSnapshot.of(metadata, evaluation, reason));
        return number;
    }

    public static Attempt of(AttemptIdentity identity, RulebookReference rulebook) {
        return new Attempt(identity, rulebook);
    }
}
