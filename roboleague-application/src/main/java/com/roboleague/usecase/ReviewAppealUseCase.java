package com.roboleague.usecase;

import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;

import java.util.Objects;

/**
 * Use case to transition an appeal into UNDER_REVIEW by an assigned arbitrator/judge.
 */
public class ReviewAppealUseCase {
    private final AppealRepository appealRepository;

    public ReviewAppealUseCase(AppealRepository appealRepository) {
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
    }

    public Appeal execute(String appealId, String reviewerId) {
        Objects.requireNonNull(reviewerId, "reviewerId cannot be null");
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        appeal.beginReview(reviewerId);
        appealRepository.save(appeal);
        return appeal;
    }
}
