package com.roboleague.usecase;

import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundSchedulerService;
import com.roboleague.scheduling.Track;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Team;
import com.roboleague.repository.EditionRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case to schedule a competition round and assign slots, tracks, and judges.
 */
public class ScheduleRoundUseCase {
    private final EditionRepository editionRepository;
    private final RoundSchedulerService schedulerService;

    public ScheduleRoundUseCase(EditionRepository editionRepository, RoundSchedulerService schedulerService) {
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService cannot be null");
    }

    public Round execute(String editionId, String categoryId, int roundNumber, String roundName,
                         List<Track> tracks, List<Judge> judges, LocalDateTime startTime,
                         Duration slotDuration, Duration interval) {
        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        List<String> teamIds = edition.getTeamsByCategory(categoryId).stream()
                .map(Team::getId)
                .toList();

        if (teamIds.isEmpty()) {
            throw new IllegalStateException("No teams registered for category " + categoryId + " in edition " + editionId);
        }

        String roundId = UUID.randomUUID().toString();
        return schedulerService.scheduleRound(
                roundId, editionId, categoryId, roundNumber, roundName,
                teamIds, tracks, judges, startTime, slotDuration, interval
        );
    }
}
