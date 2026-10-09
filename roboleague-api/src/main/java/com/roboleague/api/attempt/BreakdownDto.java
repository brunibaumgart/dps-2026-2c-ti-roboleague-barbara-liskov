package com.roboleague.api.attempt;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoreItem;
import com.roboleague.evaluation.SourceContribution;
import com.roboleague.evaluation.audit.AttemptScoreSnapshot;
import com.roboleague.usecase.AttemptBreakdown;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON view of how an attempt scored: the sources it still awaits, every item of its latest revision (bonus cap and
 * zero floor included), what each source contributed and every revision with its author and reason. Before the
 * attempt is scored, score is null and there are no items.
 */
record BreakdownDto(String attemptId, String status, int rulebookVersion, List<ResultSource> awaiting, Double score,
                    List<ItemDto> items, List<String> notes, List<SourceDto> bySource, List<RevisionDto> revisions) {

    static BreakdownDto from(AttemptBreakdown breakdown) {
        Attempt attempt = breakdown.attempt();
        ScoreBreakdown latest = attempt.getScoreBreakdown();
        return new BreakdownDto(attempt.getId().value(), attempt.getStatus().name(),
                attempt.getRulebookReference().version().number(),
                breakdown.awaitedSources().stream().sorted().toList(),
                attempt.countableScore().map(ScoreBreakdown::totalScore).orElse(null),
                ItemDto.from(latest.items()), latest.notesAndPenalties(),
                breakdown.bySource().stream().map(SourceDto::from).toList(),
                attempt.getRevisionHistory().stream().map(RevisionDto::from).toList());
    }

    record ItemDto(String concept, String rawMetric, String formula, double subtotal) {
        static List<ItemDto> from(List<ScoreItem> items) {
            return items.stream()
                    .map(item -> new ItemDto(item.concept(), item.rawMetric(), item.appliedFormula(), item.subtotal()))
                    .toList();
        }
    }

    record SourceDto(ResultSource source, String label, double subtotal, List<ItemDto> items) {
        static SourceDto from(SourceContribution contribution) {
            return new SourceDto(contribution.source(), contribution.source().label(), contribution.subtotal(),
                    ItemDto.from(contribution.items()));
        }
    }

    record RevisionDto(int number, int rulebookVersion, String authorId, String reason, double total,
                       LocalDateTime timestamp) {
        static RevisionDto from(AttemptScoreSnapshot revision) {
            return new RevisionDto(revision.revisionNumber(), revision.rulebookVersion().number(),
                    revision.authorOrJudgeId().value(), revision.reason(), revision.breakdown().totalScore(),
                    revision.timestamp());
        }
    }
}
