package com.roboleague.usecase;

import com.roboleague.port.in.TeamCandidate;
import com.roboleague.port.in.UpdateRegistration;
import com.roboleague.support.Clock;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;
import com.roboleague.port.in.RegisteredTeam;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.*;

import java.util.Objects;

/** Validates a combined team/category candidate before either repository is written. */
public class UpdateRegistrationUseCase implements UpdateRegistration {
    private final EditionRepository editions;
    private final TeamRepository teams;
    private final Clock clock;

    public UpdateRegistrationUseCase(EditionRepository editions, TeamRepository teams, Clock clock) {
        this.editions = Objects.requireNonNull(editions);
        this.teams = Objects.requireNonNull(teams);
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public RegisteredTeam execute(EditionId editionId, TeamId teamId, CategoryId categoryId, TeamCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        return execute(editionId, teamId, categoryId, candidate.toTeam(clock.now()));
    }

    /** Same update with a team already built with its dates; adapters send a {@link TeamCandidate} instead. */
    public RegisteredTeam execute(EditionId editionId, TeamId teamId, CategoryId categoryId, Team candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (!teamId.equals(candidate.getId())) {
            throw new IllegalArgumentException("A team update cannot change its identity");
        }
        Edition edition = editions.findById(editionId)
                .orElseThrow(() -> new NotFoundException("Edition", editionId));
        edition.registration(teamId)
                .orElseThrow(() -> new NotFoundException("Registration", teamId + " in " + editionId));
        teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team", teamId));
        Edition changed = edition.changeRegistrationCategory(candidate, categoryId);
        UpdateTeamUseCase.validateRegistrations(candidate, editions.findByTeamId(teamId).stream()
                .map(current -> current.getId().equals(editionId) ? changed : current).toList(), teams);
        teams.save(candidate);
        editions.save(changed);
        Registration saved = changed.registration(teamId).orElseThrow(() -> new InconsistentStateException(
                "Edition " + editionId + " lost the registration of team " + teamId + " it just saved"));
        return new RegisteredTeam(saved, candidate);
    }
}
