package com.roboleague.api.attempt;

import com.roboleague.api.ErrorDto;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.SourceReport;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.usecase.QueryTurnAttemptsUseCase;
import com.roboleague.usecase.ReceiveResultCommand;
import com.roboleague.usecase.ReceiveResultUseCase;
import com.roboleague.usecase.Reception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Driving adapter for the results of a turn. The automatic measurements and the judge panel arrive separately (F3)
 * and name the turn, not the attempt: the domain sends them to the attempt still awaiting a source or opens the next
 * one, numbered in the order it arrives. The answer is the attempt that took the result; {@code GET results} shows
 * the turn with the rulebook its category plays with and whether it takes another result.
 * Results that do not fit the rulebook, come from a judge not assigned to the slot, or exceed the attempts the
 * rulebook admits per turn are expected outcomes, so they map to 422 with every problem in details.
 */
@RestController
@RequestMapping("/slots/{slotId}")
class TurnResultsController {

    private final ReceiveResultUseCase receiveResult;
    private final QueryTurnAttemptsUseCase turnAttempts;

    TurnResultsController(ReceiveResultUseCase receiveResult, QueryTurnAttemptsUseCase turnAttempts) {
        this.receiveResult = receiveResult;
        this.turnAttempts = turnAttempts;
    }

    @GetMapping("/results")
    TurnResultsDto results(@PathVariable String slotId) {
        SlotId slot = SlotId.of(slotId);
        return TurnResultsDto.from(slot, turnAttempts.execute(slot));
    }

    @PostMapping("/measurements")
    ResponseEntity<?> measurements(@PathVariable String slotId, @RequestBody MeasurementsRequest request) {
        if (request.challengeId() == null || request.judgeId() == null || request.timeSeconds() == null
                || request.objectives() == null || request.penalties() == null || request.consumption() == null) {
            throw new IllegalArgumentException(
                    "measurements need challengeId, judgeId, timeSeconds, objectives, penalties and consumption");
        }
        SourceReport measurements = new MeasurementsBody(request.timeSeconds(), request.objectives(),
                request.penalties(), request.consumption(), request.measurements()).toReport();
        return receive(slotId, request.challengeId(), new SourceDelivery(measurements, JudgeId.of(request.judgeId())));
    }

    @PostMapping("/judge-scores")
    ResponseEntity<?> judgeScores(@PathVariable String slotId, @RequestBody JudgeScoresRequest request) {
        if (request.challengeId() == null || request.judgeId() == null || request.scores() == null) {
            throw new IllegalArgumentException("judge scores need challengeId, judgeId and scores");
        }
        SourceReport scores = new JudgeScoresBody(request.scores(), request.measurements()).toReport();
        return receive(slotId, request.challengeId(), new SourceDelivery(scores, JudgeId.of(request.judgeId())));
    }

    private ResponseEntity<?> receive(String slotId, String challengeId, SourceDelivery delivery) {
        ReceiveResultCommand command = new ReceiveResultCommand(ChallengeId.of(challengeId), SlotId.of(slotId),
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
}
