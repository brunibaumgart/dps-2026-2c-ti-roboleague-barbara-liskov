package com.roboleague.usecase;

import com.roboleague.scheduling.*;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Team;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case to schedule a competition round and assign slots, tracks, and judges.
 */
public class ScheduleRoundUseCase {
    private final EditionRepository editionRepository;
    private final RoundRepository roundRepository;
    private final RoundSchedulerService schedulerService;

    public ScheduleRoundUseCase(EditionRepository editionRepository, RoundRepository roundRepository,
                                RoundSchedulerService schedulerService) {
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService cannot be null");
    }

    public Round execute(ScheduleRoundCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Edition edition = editionRepository.findById(command.editionId())
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + command.editionId()));

        List<String> teamIds = edition.getTeamsByCategory(command.categoryId()).stream()
                .map(Team::getId)
                .toList();

        if (teamIds.isEmpty()) {
            throw new IllegalStateException("No teams registered for category " + command.categoryId()
                    + " in edition " + command.editionId());
        }

        String roundId = UUID.randomUUID().toString();
        RoundScope scope = RoundScope.of(command.editionId(), command.categoryId(), command.roundNumber());
        RoundInfo info = RoundInfo.of(roundId, command.roundName(), scope);
        RoundResources resources = RoundResources.of(command.tracks(), command.judges());
        RoundScheduleTiming timing = RoundScheduleTiming.of(command.startTime(), command.slotDuration(), command.interval());
        RoundScheduleRequest request = RoundScheduleRequest.of(info, resources, timing);

        Round round = schedulerService.scheduleRound(request, teamIds);
        roundRepository.save(round);
        return round;
    }
}
