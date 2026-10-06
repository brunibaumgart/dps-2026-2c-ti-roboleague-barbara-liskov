package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Attempt aggregate root in the evaluation bounded context.
 * Implements an append-only audit trail and snapshot revisions. It is scored with the rulebook version that was
 * current when it was captured, and every revision records that version.
 */
public class Attempt {

    public enum AttemptStatus {
        SCHEDULED("scheduled"),
        AWAITING_SOURCES("awaiting sources"),
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

    private final Map<ResultSource, SourceDelivery> received;
    private final List<AttemptScoreSnapshot> revisionHistory;
    private final List<AttemptEvent> eventHistory;

    public Attempt(AttemptIdentity identity, RulebookReference rulebook) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        this.rulebook = Objects.requireNonNull(rulebook, "rulebook cannot be null");
        this.state = WaitingAttemptState.scheduled();
        this.received = new EnumMap<>(ResultSource.class);
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

    /**
     * What each source sent so far, in the order of the sources.
     */
    public List<SourceDelivery> getDeliveries() {
        return List.copyOf(received.values());
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

    /**
     * The score that counts for the standings: the latest revision while the attempt is scored, nothing before
     * it is scored or once it is disqualified.
     */
    public Optional<ScoreBreakdown> countableScore() {
        if (!state.counts()) {
            return Optional.empty();
        }
        return Optional.of(getLatestSnapshot().breakdown());
    }

    public RawMetrics getLatestMetrics() {
        AttemptScoreSnapshot latest = getLatestSnapshot();
        return latest != null ? latest.metrics() : null;
    }

    /**
     * Takes what one source sent (F3). The measurements are checked against the rulebook first: when they do not
     * fit, nothing changes and the problems come back. The attempt is scored when the last source its rulebook
     * needs arrives; until then it awaits the others.
     */
    public MeasurementCheck receive(SourceDelivery delivery, Rulebook rulebook) {
        Objects.requireNonNull(delivery, "delivery cannot be null");
        this.rulebook.requireMatch(rulebook);
        ResultSource source = delivery.source();
        Set<ResultSource> missing = EnumSet.noneOf(ResultSource.class);
        missing.addAll(rulebook.requiredSources());
        missing.removeAll(received.keySet());
        missing.remove(source);
        AttemptState next = state.sourceReceived(missing.isEmpty());
        if (!rulebook.requiredSources().contains(source)) {
            return new MeasurementCheck.Rejected(List.of(this.rulebook + " takes no results from " + source));
        }
        if (received.containsKey(source)) {
            throw new IllegalStateException(source + " already arrived for attempt " + getId()
                    + "; corrections go through a fault adjustment or an appeal");
        }
        MeasurementCheck check = rulebook.check(source, delivery.report().named());
        if (check instanceof MeasurementCheck.Rejected) {
            return check;
        }

        received.put(source, delivery);
        eventHistory.add(SourceReceivedEvent.create(getId().value(), source, delivery.judgeId()));
        if (missing.isEmpty()) {
            EvaluationSnapshot evaluation = scored(everythingReceived(), rulebook);
            addRevision(evaluation, delivery.judgeId(), "Every source arrived");
            eventHistory.add(ResultRegisteredEvent.create(getId().value(), getTeamId(), evaluation, delivery.judgeId()));
        }
        this.state = next;
        return check;
    }

    /**
     * The sources its rulebook still needs before the attempt can be scored; none once it is scored.
     */
    public Set<ResultSource> awaitedSources(Rulebook rulebook) {
        this.rulebook.requireMatch(rulebook);
        Set<ResultSource> awaited = EnumSet.noneOf(ResultSource.class);
        awaited.addAll(rulebook.requiredSources());
        awaited.removeAll(received.keySet());
        return awaited;
    }

    /**
     * What the rules of each source contributed to the latest revision, so a mixed challenge explains each source
     * on its own (F3). Nothing before the attempt is scored.
     */
    public List<SourceContribution> contributionsBySource(Rulebook rulebook) {
        this.rulebook.requireMatch(rulebook);
        if (revisionHistory.isEmpty()) {
            return List.of();
        }
        return rulebook.contributions(getLatestMetrics());
    }

    private RawMetrics everythingReceived() {
        RawMetrics metrics = RawMetrics.nothingMeasured();
        for (SourceDelivery delivery : received.values()) {
            metrics = delivery.report().addTo(metrics);
        }
        return metrics;
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
