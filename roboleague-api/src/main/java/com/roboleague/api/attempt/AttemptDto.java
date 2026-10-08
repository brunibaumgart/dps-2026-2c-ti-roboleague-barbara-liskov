package com.roboleague.api.attempt;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.SourceDelivery;

import java.util.List;

/**
 * JSON view of an attempt: its turn, the rulebook version it is scored with, its status, the sources that already
 * arrived and the score that counts, which is null while it awaits a source or once it is disqualified.
 */
record AttemptDto(String id, String slotId, String roundId, String teamId, String challengeId, int rulebookVersion,
                  String status, List<ResultSource> received, Double score) {

    static AttemptDto from(Attempt attempt) {
        return new AttemptDto(attempt.getId().value(), attempt.getSlotId().value(), attempt.getRoundId().value(), attempt.getTeamId().value(),
                attempt.getRulebookReference().challengeId().value(), attempt.getRulebookReference().version().number(),
                attempt.getStatus().name(),
                attempt.getDeliveries().stream().map(SourceDelivery::source).toList(),
                attempt.countableScore().map(ScoreBreakdown::totalScore).orElse(null));
    }
}
