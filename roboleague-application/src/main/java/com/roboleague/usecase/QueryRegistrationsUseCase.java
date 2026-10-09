package com.roboleague.usecase;

import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.*;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class QueryRegistrationsUseCase {
    private final EditionRepository editions;
    private final TeamRepository teams;
    public QueryRegistrationsUseCase(EditionRepository editions, TeamRepository teams) {
        this.editions = Objects.requireNonNull(editions);
        this.teams = Objects.requireNonNull(teams);
    }

    public List<RegistrationView> list(EditionId editionId) {
        return edition(editionId).getRegistrations().stream()
                .sorted(Comparator.comparing(Registration::registeredAt).thenComparing(Registration::teamId))
                .map(this::view).toList();
    }

    public RegistrationView get(EditionId editionId, TeamId teamId) {
        Registration registration = edition(editionId).registration(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + teamId + " in " + editionId));
        return view(registration);
    }

    private Edition edition(EditionId id) {
        return editions.findById(id).orElseThrow(() -> new IllegalArgumentException("Edition not found: " + id));
    }

    private RegistrationView view(Registration registration) {
        Team team = teams.findById(registration.teamId())
                .orElseThrow(() -> new IllegalStateException("Registered team not found: " + registration.teamId()));
        return new RegistrationView(registration, team);
    }
}
