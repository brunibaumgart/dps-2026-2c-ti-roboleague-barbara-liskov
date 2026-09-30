package com.roboleague.api.appeal;

import com.roboleague.ranking.appeal.Appeal;

import java.time.LocalDateTime;

/**
 * JSON view of an appeal. The aggregate never leaves the API as is.
 */
record AppealDto(String id, String attemptId, String teamId, String reason, String status,
                 String reviewerId, LocalDateTime submittedAt) {

    static AppealDto from(Appeal appeal) {
        return new AppealDto(appeal.getAppealId(), appeal.getAttemptId(), appeal.getTeamId(), appeal.getReason(),
                appeal.getStatusName(), appeal.getReviewerId(), appeal.getSubmittedAt());
    }
}
