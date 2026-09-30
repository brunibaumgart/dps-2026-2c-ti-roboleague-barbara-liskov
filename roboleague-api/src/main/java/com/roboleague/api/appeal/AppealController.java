package com.roboleague.api.appeal;

import com.roboleague.usecase.ReviewAppealUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter for the appeal lifecycle. Each state transition is a sub-resource: review, acceptance, rejection.
 */
@RestController
@RequestMapping("/appeals/{appealId}")
class AppealController {

    private final ReviewAppealUseCase reviewAppeal;

    AppealController(ReviewAppealUseCase reviewAppeal) {
        this.reviewAppeal = reviewAppeal;
    }

    @PostMapping("/review")
    AppealDto review(@PathVariable String appealId, @RequestBody ReviewRequest request) {
        return AppealDto.from(reviewAppeal.execute(appealId, request.reviewerId()));
    }

    record ReviewRequest(String reviewerId) {
    }
}
