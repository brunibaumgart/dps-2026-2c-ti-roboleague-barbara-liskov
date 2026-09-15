package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Use case to file an appeal against an attempt result.
 */
public class FileAppealUseCase {
    private final AttemptRepository attemptRepository;
    private final AppealRepository appealRepository;

    public FileAppealUseCase(AttemptRepository attemptRepository, AppealRepository appealRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
    }

    public Appeal execute(String attemptId, String teamId, String reason, String evidenceDescription) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        if (!attempt.getTeamId().equals(teamId)) {
            throw new IllegalArgumentException("Team " + teamId + " does not own attempt " + attemptId);
        }

        String appealId = UUID.randomUUID().toString();
        Appeal appeal = new Appeal(appealId, attemptId, teamId, reason, evidenceDescription);

        attempt.markUnderAppeal();
        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        return appeal;
    }
}
