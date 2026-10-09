package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.support.Clock;
import com.roboleague.tournament.*;

import java.util.Objects;

/** Enrolls new or canonical teams without overwriting an existing team's state. */
public class RegisterTeamUseCase {
    private final TeamRepository teams;
    private final EditionRepository editions;
    private final Clock clock;

    public RegisterTeamUseCase(TeamRepository teams, EditionRepository editions, Clock clock) {
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Registration execute(EditionId editionId, CategoryId categoryId, Team newTeam) {
        Objects.requireNonNull(newTeam, "team cannot be null");
        Edition edition = edition(editionId);
        if (teams.findById(newTeam.getId()).isPresent()) {
            throw new IllegalStateException("Team already exists; enroll it by identity or use the controlled update: " + newTeam.getId());
        }
        Edition enrolled = edition.registerTeam(newTeam, categoryId, clock.now());
        // Validation is complete before either write. Memory adapters do not provide transactional rollback.
        teams.save(newTeam);
        editions.save(enrolled);
        return enrolled.registration(newTeam.getId()).orElseThrow();
    }

    public Registration execute(EditionId editionId, CategoryId categoryId, TeamId teamId) {
        Edition edition = edition(editionId);
        Team canonical = teams.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        Edition enrolled = edition.registerTeam(canonical, categoryId, clock.now());
        editions.save(enrolled);
        return enrolled.registration(teamId).orElseThrow();
    }

    private Edition edition(EditionId id) {
        return editions.findById(id).orElseThrow(() -> new IllegalArgumentException("Edition not found: " + id));
    }
}
