package com.roboleague.api.attempt;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.support.ActorId;
import com.roboleague.usecase.AttemptCorrection;
import com.roboleague.usecase.CorrectAttemptUseCase;
import com.roboleague.usecase.GetAttemptBreakdownUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

/**
 * Driving adapter for one attempt, once the domain opened it: its breakdown explains the score by source, and the
 * attempt id is the slot and the number the domain gave it. Results arrive by turn ({@link TurnResultsController}).
 * A scored attempt is corrected outside an appeal with a penalty adjustment or a disqualification; both answer the
 * standings version they produced.
 */
@RestController
@RequestMapping("/attempts/{attemptId}")
class AttemptController {

    private final GetAttemptBreakdownUseCase getBreakdown;
    private final CorrectAttemptUseCase correctAttempt;

    AttemptController(GetAttemptBreakdownUseCase getBreakdown, CorrectAttemptUseCase correctAttempt) {
        this.getBreakdown = getBreakdown;
        this.correctAttempt = correctAttempt;
    }

    @GetMapping("/breakdown")
    BreakdownDto breakdown(@PathVariable String attemptId) {
        return BreakdownDto.from(getBreakdown.execute(AttemptId.parse(attemptId)));
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
