package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.*;

import java.util.Objects;

/** Changes the category of one enrollment, preserving its identity, calendar and original timestamp. */
public class ChangeRegistrationCategoryUseCase {
    private final TeamRepository teams;
    private final EditionRepository editions;

    public ChangeRegistrationCategoryUseCase(TeamRepository teams, EditionRepository editions) {
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
    }

    public Registration execute(EditionId editionId, TeamId teamId, CategoryId categoryId) {
        Edition edition = editions.findById(editionId)
                .orElseThrow(() -> new NotFoundException("Edition", editionId));
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team", teamId));
        Edition changed = edition.changeRegistrationCategory(team, categoryId);
        editions.save(changed);
        return changed.registration(teamId).orElseThrow(() -> new InconsistentStateException(
                "Edition " + editionId + " lost the registration of team " + teamId + " it just saved"));
    }
}
