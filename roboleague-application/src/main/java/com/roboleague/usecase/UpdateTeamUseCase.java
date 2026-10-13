package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.eligibility.TeamIneligibleException;

import java.util.ArrayList;
import java.util.List;
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
        teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team", teamId));
        if (!teamId.equals(candidate.getId())) {
            throw new IllegalArgumentException("A team update cannot change its identity");
        }
        validateRegistrations(candidate, editions.findByTeamId(teamId), teams);
        teams.save(candidate);
        return candidate;
    }

    /**
     * Checks the candidate against every edition it is registered in: the eligibility of its category there, and
     * that it shares no participant or robot with another team of that edition. Every reason is named by edition.
     */
    static void validateRegistrations(Team candidate, List<Edition> registrations, TeamRepository teams) {
        var violations = new ArrayList<String>();
        for (var edition : registrations) {
            try {
                edition.requireEligible(candidate);
            } catch (TeamIneligibleException rejected) {
                rejected.getViolations().forEach(reason -> violations.add("Edition " + edition.getId() + ": " + reason));
            }
            EditionRosters.of(edition, teams).conflictsWith(candidate)
                    .forEach(conflict -> violations.add("Edition " + edition.getId() + ": " + conflict));
        }
        if (!violations.isEmpty()) {
            throw new TeamIneligibleException("Team update violates existing registrations", violations);
        }
    }
}
