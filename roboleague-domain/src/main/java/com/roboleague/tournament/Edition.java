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
    private final EditionContext context;
    private final DateRange dates;
    private final ScoringPolicy scoringPolicy;
    private final List<Category> categories;
    private final List<Team> registeredTeams;

    public Edition(EditionContext context, DateRange dates, ScoringPolicy scoringPolicy) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.dates = Objects.requireNonNull(dates, "dates cannot be null");
        this.scoringPolicy = Objects.requireNonNull(scoringPolicy, "scoringPolicy cannot be null");
        this.categories = new ArrayList<>();
        this.registeredTeams = new ArrayList<>();
    }

    public EditionContext getContext() {
        return context;
    }

    public String getId() {
        return context.header().id();
    }

    public Tournament getTournament() {
        return context.tournament();
    }

    public int getEditionNumber() {
        return context.header().editionNumber();
    }

    public String getName() {
        return context.header().name();
    }

    public DateRange getDates() {
        return dates;
    }

    public LocalDate getStartDate() {
        return dates.startDate();
    }

    public LocalDate getEndDate() {
        return dates.endDate();
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

    public static Edition of(EditionContext context, DateRange dates, ScoringPolicy scoringPolicy) {
        return new Edition(context, dates, scoringPolicy);
    }

    public static Edition of(String id, Tournament tournament, int editionNumber, String name,
                              LocalDate startDate, LocalDate endDate, ScoringPolicy scoringPolicy, List<Category> categories) {
        Edition edition = new Edition(
                new EditionContext(tournament, new EditionHeader(id, name, editionNumber)),
                new DateRange(startDate, endDate),
                scoringPolicy
        );
        if (categories != null) {
            categories.forEach(edition::addCategory);
        }
        return edition;
    }
}
