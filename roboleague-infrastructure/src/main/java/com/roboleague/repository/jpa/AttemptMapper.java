package com.roboleague.repository.jpa;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.AttemptProgress;
import com.roboleague.evaluation.AttemptStage;
import com.roboleague.evaluation.JudgeScores;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoreItem;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.audit.AppealAcceptedEvent;
import com.roboleague.evaluation.audit.AppealResolution;
import com.roboleague.evaluation.audit.AttemptDisqualifiedEvent;
import com.roboleague.evaluation.audit.AttemptEvent;
import com.roboleague.evaluation.audit.AttemptScoreSnapshot;
import com.roboleague.evaluation.audit.AuditAuthor;
import com.roboleague.evaluation.audit.AuditTrail;
import com.roboleague.evaluation.audit.EvaluationSnapshot;
import com.roboleague.evaluation.audit.EventMetadata;
import com.roboleague.evaluation.audit.PenaltyAppliedEvent;
import com.roboleague.evaluation.audit.PenaltyDetail;
import com.roboleague.evaluation.audit.ResultRegisteredEvent;
import com.roboleague.evaluation.audit.ScoreAdjustedEvent;
import com.roboleague.evaluation.audit.ScoreAdjustmentDetails;
import com.roboleague.evaluation.audit.SnapshotIdentity;
import com.roboleague.evaluation.audit.SnapshotMetadata;
import com.roboleague.evaluation.audit.SourceReceivedEvent;
import com.roboleague.evaluation.audit.TeamJudgeBinding;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;
import com.roboleague.repository.jpa.AttemptJpaEntity.BreakdownJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.DeliveryJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.EvaluationJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.EventJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.HistoryJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.ItemJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.RevisionJson;
import com.roboleague.repository.jpa.AttemptJpaEntity.SectionJson;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Anti-corruption layer between the attempts table and the {@link Attempt} aggregate. The kinds of source and of
 * event are closed sets in the domain, so translating them is the one place that asks which one it is.
 */
final class AttemptMapper {

    private AttemptMapper() {
    }

    static AttemptJpaEntity toEntity(Attempt attempt) {
        AttemptJpaEntity entity = new AttemptJpaEntity();
        entity.id = attempt.getId().value();
        entity.slotId = attempt.getId().slotId();
        entity.attemptNumber = attempt.getId().number();
        entity.roundId = attempt.getRoundId();
        entity.teamId = attempt.getTeamId();
        entity.challengeId = attempt.getRulebookReference().challengeId();
        entity.rulebookVersion = attempt.getRulebookReference().version().number();
        AttemptStage stage = attempt.getStage();
        entity.status = stage.status().name();
        entity.openAppeals = stage.openAppeals();
        entity.settledStatus = stage.settledStatus() == null ? null : stage.settledStatus().name();
        entity.history = new HistoryJson(
                attempt.getDeliveries().stream().map(AttemptMapper::toJson).toList(),
                attempt.getRevisionHistory().stream().map(AttemptMapper::toJson).toList(),
                attempt.getEventHistory().stream().map(AttemptMapper::toJson).toList());
        return entity;
    }

    static Attempt toDomain(AttemptJpaEntity entity) {
        AttemptIdentity identity = new AttemptIdentity(AttemptId.of(entity.slotId, entity.attemptNumber),
                entity.roundId, entity.teamId);
        RulebookReference rulebook = new RulebookReference(entity.challengeId,
                new RulebookVersion(entity.rulebookVersion));
        AttemptStage stage = new AttemptStage(AttemptStatus.valueOf(entity.status), entity.openAppeals,
                entity.settledStatus == null ? null : AttemptStatus.valueOf(entity.settledStatus));
        AuditTrail trail = new AuditTrail(
                entity.history.revisions().stream().map(AttemptMapper::toRevision).toList(),
                entity.history.events().stream().map(json -> toEvent(entity.id, json)).toList());
        List<SourceDelivery> deliveries = entity.history.deliveries().stream().map(AttemptMapper::toDelivery).toList();
        return Attempt.restore(identity, rulebook, new AttemptProgress(stage, deliveries, trail));
    }

    private static DeliveryJson toJson(SourceDelivery delivery) {
        RawMetrics sent = delivery.report().addTo(RawMetrics.nothingMeasured());
        return new DeliveryJson(delivery.source().name(), delivery.judgeId(), MetricsJson.of(sent));
    }

    private static SourceDelivery toDelivery(DeliveryJson json) {
        MetricsJson sent = json.sent();
        return new SourceDelivery(switch (ResultSource.valueOf(json.source())) {
            case AUTOMATIC_MEASUREMENTS -> new Measurements(
                    new TrackPerformance(sent.timeTakenSeconds(), sent.objectivesCompleted(), sent.penaltiesCount()),
                    sent.resourceConsumption(), sent.customMetrics());
            case JUDGE_PANEL -> new JudgeScores(sent.judgeSubjectiveScores(), sent.customMetrics());
        }, json.judgeId());
    }

    private static RevisionJson toJson(AttemptScoreSnapshot revision) {
        return new RevisionJson(revision.revisionNumber(), revision.snapshotId(), revision.authorOrJudgeId(),
                revision.timestamp().toString(), revision.reason(), toJson(revision.evaluation()));
    }

    private static AttemptScoreSnapshot toRevision(RevisionJson json) {
        return new AttemptScoreSnapshot(
                new SnapshotMetadata(new SnapshotIdentity(json.snapshotId(), json.number()),
                        new AuditAuthor(json.authorId(), LocalDateTime.parse(json.timestamp()))),
                toEvaluation(json.evaluation()), json.reason());
    }

    private static EvaluationJson toJson(EvaluationSnapshot evaluation) {
        ScoreBreakdown breakdown = evaluation.breakdown();
        return new EvaluationJson(evaluation.version().number(), MetricsJson.of(evaluation.metrics()),
                new BreakdownJson(toJson(breakdown.base()), toJson(breakdown.bonuses()),
                        toJson(breakdown.deductions())));
    }

    private static EvaluationSnapshot toEvaluation(EvaluationJson json) {
        BreakdownJson breakdown = json.breakdown();
        return new EvaluationSnapshot(new RulebookVersion(json.rulebookVersion()), json.metrics().toMetrics(),
                new ScoreBreakdown(toSection(breakdown.base()), toSection(breakdown.bonuses()),
                        toSection(breakdown.deductions())));
    }

    private static SectionJson toJson(RuleEvaluation section) {
        return new SectionJson(section.items().stream()
                .map(item -> new ItemJson(item.concept(), item.rawMetric(), item.appliedFormula(), item.subtotal()))
                .toList(), section.notes());
    }

    private static RuleEvaluation toSection(SectionJson json) {
        return new RuleEvaluation(json.items().stream()
                .map(item -> ScoreItem.of(item.concept(), item.rawMetric(), item.formula(), item.subtotal()))
                .toList(), json.notes());
    }

    private static EventJson toJson(AttemptEvent event) {
        String type = event.eventType();
        String id = event.eventId();
        String at = event.timestamp().toString();
        return switch (event) {
            case SourceReceivedEvent received -> new EventJson(type, id, at, received.judgeId(),
                    received.source().name(), null, null, null, null, null);
            case ResultRegisteredEvent registered -> new EventJson(type, id, at, registered.judgeId(), null, null,
                    null, null, registered.teamId(), toJson(registered.evaluation()));
            case PenaltyAppliedEvent penalty -> new EventJson(type, id, at, penalty.judgeId(), null, penalty.reason(),
                    penalty.additionalPenalties(), null, null, null);
            case ScoreAdjustedEvent adjusted -> new EventJson(type, id, at, adjusted.authorId(), null,
                    adjusted.reason(), adjusted.newRevisionNumber(), null, null, toJson(adjusted.evaluation()));
            case AppealAcceptedEvent accepted -> new EventJson(type, id, at, accepted.reviewerId(), null,
                    accepted.resolutionNotes(), null, accepted.appealId(), null, null);
            case AttemptDisqualifiedEvent disqualified -> new EventJson(type, id, at, disqualified.judgeId(), null,
                    disqualified.reason(), null, null, null, null);
        };
    }

    private static AttemptEvent toEvent(String attemptId, EventJson json) {
        EventMetadata metadata = new EventMetadata(json.eventId(), attemptId, LocalDateTime.parse(json.timestamp()));
        return switch (json.type()) {
            case "SOURCE_RECEIVED" ->
                    new SourceReceivedEvent(metadata, ResultSource.valueOf(json.source()), json.actorId());
            case "RESULT_REGISTERED" -> new ResultRegisteredEvent(metadata, toEvaluation(json.evaluation()),
                    new TeamJudgeBinding(json.teamId(), json.actorId()));
            case "PENALTY_APPLIED" ->
                    new PenaltyAppliedEvent(metadata, new PenaltyDetail(json.number(), json.reason()), json.actorId());
            case "SCORE_ADJUSTED" -> new ScoreAdjustedEvent(metadata, toEvaluation(json.evaluation()),
                    new ScoreAdjustmentDetails(json.number(), json.reason(), json.actorId()));
            case "APPEAL_ACCEPTED" -> new AppealAcceptedEvent(metadata,
                    new AppealResolution(json.appealId(), json.reason()), json.actorId());
            case "ATTEMPT_DISQUALIFIED" -> new AttemptDisqualifiedEvent(metadata, json.reason(), json.actorId());
            default -> throw new IllegalStateException("Unknown stored attempt event: " + json.type());
        };
    }
}
