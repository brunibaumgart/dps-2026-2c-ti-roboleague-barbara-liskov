package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.TeamId;

import java.util.Objects;

/**
 * Use case to file an appeal against an attempt result.
 */
public class FileAppealUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AttemptRepository attemptRepository;
    private final AppealRepository appealRepository;

    public FileAppealUseCase(AttemptRepository attemptRepository, AppealRepository appealRepository, Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
    }

    public Appeal execute(AttemptId attemptId, TeamId teamId, String reason, String evidenceDescription) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        if (!attempt.getTeamId().equals(teamId)) {
            throw new IllegalArgumentException("Team " + teamId + " does not own attempt " + attemptId);
        }

        AppealId appealId = AppealId.of(ids.nextId());
        Appeal appeal = Appeal.of(appealId, attempt.getId(), teamId, reason, evidenceDescription, clock.now());

        attempt.markUnderAppeal();
        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        return appeal;
    }
}
