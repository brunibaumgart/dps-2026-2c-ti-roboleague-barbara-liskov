package com.roboleague.api.attempt;

import com.roboleague.api.ErrorDto;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.SourceReport;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.usecase.AttemptCorrection;
import com.roboleague.usecase.CorrectAttemptUseCase;
import com.roboleague.usecase.GetAttemptBreakdownUseCase;
import com.roboleague.usecase.ReceiveResultCommand;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.Reception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

/**
 * Driving adapter for an attempt's results. The automatic measurements and the judge panel arrive separately (F3),
 * each to its own sub-resource of the attempt, and the breakdown explains the score by source; the attempt id is the
 * slot and the attempt number.
 * Results that do not fit the rulebook, or come from a judge not assigned to the slot, are an expected outcome
 * of the use case, so they map to 422 here with every problem in details. A scored attempt is corrected outside an
 * appeal with a penalty adjustment or a disqualification; both answer the standings version they produced.
 */
@RestController
@RequestMapping("/attempts/{attemptId}")
class AttemptController {

    private final ReceiveResultUseCase receiveResult;
    private final GetAttemptBreakdownUseCase getBreakdown;
    private final CorrectAttemptUseCase correctAttempt;

    AttemptController(ReceiveResultUseCase receiveResult, GetAttemptBreakdownUseCase getBreakdown,
                      CorrectAttemptUseCase correctAttempt) {
        this.receiveResult = receiveResult;
        this.getBreakdown = getBreakdown;
        this.correctAttempt = correctAttempt;
    }

    @GetMapping("/breakdown")
    BreakdownDto breakdown(@PathVariable String attemptId) {
        return BreakdownDto.from(getBreakdown.execute(AttemptId.parse(attemptId)));
    }

    @PutMapping("/measurements")
    ResponseEntity<?> measurements(@PathVariable String attemptId, @RequestBody MeasurementsRequest request) {
        if (request.challengeId() == null || request.judgeId() == null || request.timeSeconds() == null
                || request.objectives() == null || request.penalties() == null || request.consumption() == null) {
            throw new IllegalArgumentException(
                    "measurements need challengeId, judgeId, timeSeconds, objectives, penalties and consumption");
        }
        SourceReport measurements = new MeasurementsBody(request.timeSeconds(), request.objectives(),
                request.penalties(), request.consumption(), request.measurements()).toReport();
        return receive(attemptId, request.challengeId(), new SourceDelivery(measurements, JudgeId.of(request.judgeId())));
    }

    @PutMapping("/judge-scores")
    ResponseEntity<?> judgeScores(@PathVariable String attemptId, @RequestBody JudgeScoresRequest request) {
        if (request.challengeId() == null || request.judgeId() == null || request.scores() == null) {
            throw new IllegalArgumentException("judge scores need challengeId, judgeId and scores");
        }
        SourceReport scores = new JudgeScoresBody(request.scores(), request.measurements()).toReport();
        return receive(attemptId, request.challengeId(), new SourceDelivery(scores, JudgeId.of(request.judgeId())));
    }

    @PostMapping("/penalty-adjustments")
    CorrectionDto adjustPenalties(@PathVariable String attemptId, @RequestBody PenaltyAdjustmentRequest request) {
        AuditNote note = AuditNote.of(ActorId.of(text(request.actorId(), "actorId")), text(request.reason(), "reason"));
        return CorrectionDto.from(correctAttempt.adjustPenalties(AttemptId.parse(attemptId),
                required(request.additionalPenalties(), "additionalPenalties"), note));
    }

    @PostMapping("/disqualification")
    CorrectionDto disqualify(@PathVariable String attemptId, @RequestBody DisqualificationRequest request) {
        return CorrectionDto.from(correctAttempt.disqualify(AttemptId.parse(attemptId),
                JudgeId.of(text(request.judgeId(), "judgeId")), text(request.reason(), "reason")));
    }

    private ResponseEntity<?> receive(String attemptId, String challengeId, SourceDelivery delivery) {
        ReceiveResultCommand command = new ReceiveResultCommand(ChallengeId.of(challengeId), AttemptId.parse(attemptId),
                delivery);
        return switch (receiveResult.execute(command)) {
            case Reception.Received received -> ResponseEntity.ok(AttemptDto.from(received.attempt()));
            case Reception.Rejected rejected -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ErrorDto("Result rejected", rejected.problems()));
        };
    }

    record MeasurementsRequest(String challengeId, String judgeId, Double timeSeconds, Integer objectives,
                               Integer penalties, Double consumption, Map<String, Double> measurements) {
    }

    record JudgeScoresRequest(String challengeId, String judgeId, Map<String, Double> scores,
                              Map<String, Double> measurements) {
    }

    record PenaltyAdjustmentRequest(String actorId, String reason, Integer additionalPenalties) {
    }

    record DisqualificationRequest(String judgeId, String reason) {
    }

    /**
     * The corrected attempt and the standings version its correction produced.
     */
    record CorrectionDto(AttemptDto attempt, int standingsVersion) {
        static CorrectionDto from(AttemptCorrection correction) {
            return new CorrectionDto(AttemptDto.from(correction.attempt()), correction.standings().number());
        }
    }
}
