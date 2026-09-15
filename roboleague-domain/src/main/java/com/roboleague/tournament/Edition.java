package com.roboleague.tournament;

import com.roboleague.evaluation.ScoringPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Edition of a tournament.
 * Publishes and binds an immutable version of the ScoringPolicy (Rulebook).
 */
public class Edition {
    private final String id;
    private final Tournament tournament;
    private final int editionNumber;
    private final String name;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ScoringPolicy scoringPolicy;
    private final List<Category> categories;
    private final List<Team> registeredTeams;

    public Edition(String id, Tournament tournament, int editionNumber, String name,
                   LocalDate startDate, LocalDate endDate, ScoringPolicy scoringPolicy,
                   List<Category> categories) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tournament = Objects.requireNonNull(tournament, "tournament cannot be null");
        this.editionNumber = editionNumber;
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
        this.scoringPolicy = Objects.requireNonNull(scoringPolicy, "scoringPolicy cannot be null");
        this.categories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
        this.registeredTeams = new ArrayList<>();

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
    }

    public String getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public int getEditionNumber() {
        return editionNumber;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ScoringPolicy getScoringPolicy() {
        return scoringPolicy;
    }

    public List<Category> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<Team> getRegisteredTeams() {
        return Collections.unmodifiableList(registeredTeams);
    }

    public void addCategory(Category category) {
        Objects.requireNonNull(category, "category cannot be null");
        if (!categories.contains(category)) {
            categories.add(category);
        }
    }

    public void registerTeam(Team team) {
        Objects.requireNonNull(team, "team cannot be null");
        if (registeredTeams.contains(team)) {
            throw new IllegalArgumentException("Team already registered in this edition: " + team.getName());
        }
        if (!categories.contains(team.getCategory())) {
            throw new IllegalArgumentException("Team category " + team.getCategory().name() + " is not offered in this edition");
        }
        registeredTeams.add(team);
    }

    public List<Team> getTeamsByCategory(String categoryId) {
        return registeredTeams.stream()
                .filter(t -> t.getCategory().id().equals(categoryId))
                .toList();
    }
}
