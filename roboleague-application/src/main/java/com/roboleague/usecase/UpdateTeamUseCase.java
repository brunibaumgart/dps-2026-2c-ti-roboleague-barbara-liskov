package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.eligibility.TeamIneligibleException;

import java.util.ArrayList;
import java.util.Objects;

/** Replaces canonical immutable state only after validating every enrollment. */
public class UpdateTeamUseCase {
    private final TeamRepository teams;
    private final EditionRepository editions;

    public UpdateTeamUseCase(TeamRepository teams, EditionRepository editions) {
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
    }

    public Team execute(TeamId teamId, Team candidate) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        teams.findById(teamId).orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        if (!teamId.equals(candidate.getId())) {
            throw new IllegalArgumentException("A team update cannot change its identity");
        }
        var violations = new ArrayList<String>();
        for (var edition : editions.findByTeamId(teamId)) {
            try {
                edition.requireEligible(candidate);
            } catch (TeamIneligibleException rejected) {
                rejected.getViolations().forEach(reason -> violations.add("Edition " + edition.getId() + ": " + reason));
            }
        }
        if (!violations.isEmpty()) {
            throw new TeamIneligibleException("Team update violates existing registrations", violations);
        }
        teams.save(candidate);
        return candidate;
    }
}
