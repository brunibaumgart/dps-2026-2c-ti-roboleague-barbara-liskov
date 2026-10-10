package com.roboleague.api.appeal;

import com.roboleague.api.ErrorDto;
import com.roboleague.api.attempt.JudgeScoresBody;
import com.roboleague.api.attempt.MeasurementsBody;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.SourceReport;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import com.roboleague.usecase.AppealAcceptance;
import com.roboleague.usecase.FileAppealUseCase;
import com.roboleague.usecase.QueryAppealsUseCase;
import com.roboleague.usecase.ResolveAppealUseCase;
import com.roboleague.usecase.ReviewAppealUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static com.roboleague.api.RequestValues.text;

/**
 * Driving adapter for the appeal lifecycle: a team files it against one of its attempts, and each state transition
 * is a sub-resource of the appeal (review, acceptance, rejection). Accepting it carries the corrected report of
 * each disputed source; corrections that do not fit the attempt's rulebook are an expected outcome of the use case,
 * so they map to 422 here with every problem in details.
 */
@RestController
class AppealController {

    private final FileAppealUseCase fileAppeal;
    private final ReviewAppealUseCase reviewAppeal;
    private final ResolveAppealUseCase resolveAppeal;
    private final QueryAppealsUseCase queryAppeals;

    AppealController(FileAppealUseCase fileAppeal, ReviewAppealUseCase reviewAppeal, ResolveAppealUseCase resolveAppeal,
                     QueryAppealsUseCase queryAppeals) {
        this.fileAppeal = fileAppeal;
        this.reviewAppeal = reviewAppeal;
        this.resolveAppeal = resolveAppeal;
        this.queryAppeals = queryAppeals;
    }

    @PostMapping("/attempts/{attemptId}/appeals")
    ResponseEntity<AppealDto> file(@PathVariable String attemptId, @RequestBody FileRequest request) {
        AppealDto filed = AppealDto.from(fileAppeal.execute(AttemptId.parse(attemptId),
                TeamId.of(text(request.teamId(), "teamId")), text(request.reason(), "reason"),
                request.evidence() != null ? request.evidence() : ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(filed);
    }

    @GetMapping("/attempts/{attemptId}/appeals")
    List<AppealDto> ofAttempt(@PathVariable String attemptId) {
        return queryAppeals.ofAttempt(AttemptId.parse(attemptId)).stream().map(AppealDto::from).toList();
    }

    @GetMapping("/challenges/{challengeId}/appeals")
    List<AppealDto> ofCategory(@PathVariable String challengeId, @RequestParam String categoryId) {
        return queryAppeals.ofCategory(new StandingsId(ChallengeId.of(challengeId), CategoryId.of(categoryId)))
                .stream().map(AppealDto::from).toList();
    }

    @GetMapping("/appeals/{appealId}")
    AppealDto get(@PathVariable String appealId) {
        return AppealDto.from(queryAppeals.get(AppealId.of(appealId)));
    }

    @PostMapping("/appeals/{appealId}/review")
    AppealDto review(@PathVariable String appealId, @RequestBody ReviewRequest request) {
        return AppealDto.from(reviewAppeal.execute(AppealId.of(appealId), ActorId.of(request.reviewerId())));
    }

    @PostMapping("/appeals/{appealId}/acceptance")
    ResponseEntity<?> accept(@PathVariable String appealId, @RequestBody AcceptanceRequest request) {
        List<SourceReport> corrections = new ArrayList<>();
        if (request.measurements() != null) {
            corrections.add(request.measurements().toReport());
        }
        if (request.judgeScores() != null) {
            corrections.add(request.judgeScores().toReport());
        }
        AuditNote resolution = resolution(request.reviewerId(), request.notes());
        return switch (resolveAppeal.acceptAppeal(AppealId.of(appealId), resolution, corrections)) {
            case AppealAcceptance.Accepted accepted -> ResponseEntity.ok(
                    new AcceptanceDto(AppealDto.from(accepted.appeal()), accepted.standings().number()));
            case AppealAcceptance.Invalid invalid -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ErrorDto("Corrections rejected", invalid.problems()));
        };
    }

    @PostMapping("/appeals/{appealId}/rejection")
    AppealDto reject(@PathVariable String appealId, @RequestBody RejectionRequest request) {
        return AppealDto.from(resolveAppeal.rejectAppeal(AppealId.of(appealId),
                resolution(request.reviewerId(), request.notes())));
    }

    private static AuditNote resolution(String reviewerId, String notes) {
        return AuditNote.of(ActorId.of(text(reviewerId, "reviewerId")), text(notes, "notes"));
    }

    record FileRequest(String teamId, String reason, String evidence) {
    }

    record ReviewRequest(String reviewerId) {
    }

    record AcceptanceRequest(String reviewerId, String notes, MeasurementsBody measurements,
                             JudgeScoresBody judgeScores) {
    }

    record RejectionRequest(String reviewerId, String notes) {
    }

    /**
     * The accepted appeal and the standings version its correction produced.
     */
    record AcceptanceDto(AppealDto appeal, int standingsVersion) {
    }
}
