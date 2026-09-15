package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.eligibility.EligibilityResult;
import com.roboleague.tournament.eligibility.EligibilitySpecification;

import java.util.Objects;

/**
 * Use case to register a team into a tournament edition after enforcing eligibility specifications.
 */
public class RegisterTeamUseCase {
    private final TeamRepository teamRepository;
    private final EditionRepository editionRepository;
    private final EligibilitySpecification<Team> eligibilitySpecification;

    public RegisterTeamUseCase(TeamRepository teamRepository,
                               EditionRepository editionRepository,
                               EligibilitySpecification<Team> eligibilitySpecification) {
        this.teamRepository = Objects.requireNonNull(teamRepository, "teamRepository cannot be null");
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.eligibilitySpecification = Objects.requireNonNull(eligibilitySpecification, "eligibilitySpecification cannot be null");
    }

    public Team execute(String editionId, Team team) {
        Objects.requireNonNull(team, "team cannot be null");
        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        EligibilityResult result = eligibilitySpecification.isSatisfiedBy(team);
        if (!result.isEligible()) {
            throw new TeamIneligibleException("Team " + team.getName() + " failed eligibility requirements", result.reasons());
        }

        edition.registerTeam(team);
        teamRepository.save(team);
        editionRepository.save(edition);

        return team;
    }
}
