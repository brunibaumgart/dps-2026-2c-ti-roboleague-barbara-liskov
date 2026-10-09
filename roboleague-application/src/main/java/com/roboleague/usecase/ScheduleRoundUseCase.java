package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.*;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Use case to schedule a competition round and assign slots, tracks, and judges.
 */
public class ScheduleRoundUseCase {
    private final IdGenerator ids;

    private final ChallengeRepository challengeRepository;
    private final EditionRepository editionRepository;
    private final TeamRepository teamRepository;
    private final RoundRepository roundRepository;
    private final RoundSchedulerService schedulerService;

    public ScheduleRoundUseCase(ChallengeRepository challengeRepository, EditionRepository editionRepository, TeamRepository teamRepository, RoundRepository roundRepository,
                                RoundSchedulerService schedulerService, IdGenerator ids) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.teamRepository = Objects.requireNonNull(teamRepository, "teamRepository cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService cannot be null");
    }

    public Round execute(ScheduleRoundCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Edition edition = editionRepository.findById(command.editionId())
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + command.editionId()));

        Challenge challenge = challengeRepository.findById(command.challengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + command.challengeId()));
        if (!challenge.getEditionId().equals(edition.getId())) {
            throw new IllegalArgumentException("Challenge belongs to another edition");
        }
        RoundScope scope = RoundScope.of(command.challengeId(), command.editionId(), command.categoryId(), command.roundNumber());
        if (roundRepository.findByScope(scope).isPresent()) {
            throw new IllegalStateException("Round scope already exists: " + scope);
        }
        RoundResources resources = RoundResources.of(command.tracks(), command.judges());
        RoundScheduleTiming timing = RoundScheduleTiming.of(command.startTime(), command.slotDuration(), command.interval());

        List<TeamId> teamIds = edition.getRegistrationsByCategory(command.categoryId()).stream()
                .sorted(Comparator.comparing(Registration::registeredAt).thenComparing(Registration::teamId))
                .map(registration -> {
                    Team team = teamRepository.findById(registration.teamId())
                            .orElseThrow(() -> new IllegalStateException("Registered team not found: " + registration.teamId()));
                    edition.requireEligible(team);
                    return team.getId();
                })
                .toList();

        if (teamIds.isEmpty()) {
            throw new IllegalStateException("No teams registered for category " + command.categoryId()
                    + " in edition " + command.editionId());
        }

        RoundId roundId = RoundId.of(ids.nextId());
        RoundInfo info = RoundInfo.of(roundId, command.roundName(), scope);
        RoundScheduleRequest request = RoundScheduleRequest.of(info, resources, timing);

        Round round = schedulerService.scheduleRound(request, teamIds, roundRepository.findAll());
        roundRepository.save(round);
        return round;
    }
}
