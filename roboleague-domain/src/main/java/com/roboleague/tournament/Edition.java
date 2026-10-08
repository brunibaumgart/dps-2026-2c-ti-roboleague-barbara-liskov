package com.roboleague.tournament;

import com.roboleague.tournament.eligibility.RegistrationEligibility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edition of a tournament.
 * Its challenges, each with its own rulebook, are separate aggregates that reference it by id.
 */
public final class Edition {
    private final EditionContext context;
    private final DateRange dates;
    private final List<Category> categories;
    private final List<Registration> registrations;

    public Edition(EditionContext context, DateRange dates) {
        this(context, dates, List.of(), List.of());
    }

    private Edition(EditionContext context, DateRange dates, List<Category> categories,
                    List<Registration> registrations) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.dates = Objects.requireNonNull(dates, "dates cannot be null");
        this.categories = List.copyOf(categories);
        this.registrations = List.copyOf(registrations);
        var categoryIds = new HashSet<CategoryId>();
        for (Category category : this.categories) {
            if (!categoryIds.add(category.id())) {
                throw new IllegalArgumentException("Category already offered in this edition: " + category.id());
            }
        }
        var teams = new HashSet<TeamId>();
        for (Registration registration : this.registrations) {
            if (!registration.editionId().equals(getId()) || !registration.referenceDate().equals(getStartDate())) {
                throw new IllegalArgumentException("Registration must belong to the edition and its start date");
            }
            category(registration.categoryId());
            if (!teams.add(registration.teamId())) {
                throw new IllegalStateException("Team already registered in this edition: " + registration.teamId());
            }
        }
    }

    public EditionContext getContext() {
        return context;
    }

    public EditionId getId() {
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

    public List<Category> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<Registration> getRegistrations() {
        return registrations;
    }

    public Optional<Registration> registration(TeamId teamId) {
        return registrations.stream().filter(r -> r.teamId().equals(teamId)).findFirst();
    }

    public Category category(CategoryId categoryId) {
        return categories.stream().filter(c -> c.id().equals(categoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Category " + categoryId + " is not offered in edition " + getId()));
    }

    public Edition addCategory(Category category) {
        Objects.requireNonNull(category, "category cannot be null");
        var added = new ArrayList<>(categories);
        added.add(category);
        return new Edition(context, dates, added, registrations);
    }

    public Edition registerTeam(Team team, CategoryId categoryId, LocalDateTime registeredAt) {
        Objects.requireNonNull(team, "team cannot be null");
        if (registration(team.getId()).isPresent()) {
            throw new IllegalStateException("Team already registered in this edition: " + team.getId());
        }
        RegistrationEligibility.requireEligible(team, category(categoryId), getStartDate());
        var added = new ArrayList<>(registrations);
        added.add(new Registration(team.getId(), getId(), categoryId, getStartDate(), registeredAt));
        return new Edition(context, dates, categories, added);
    }

    public Edition changeRegistrationCategory(Team team, CategoryId categoryId) {
        Registration original = registration(team.getId())
                .orElseThrow(() -> new IllegalArgumentException("Team not registered in edition: " + team.getId()));
        RegistrationEligibility.requireEligible(team, category(categoryId), original.referenceDate());
        var changed = registrations.stream().map(r -> r.teamId().equals(team.getId())
                ? new Registration(r.teamId(), r.editionId(), categoryId, r.referenceDate(), r.registeredAt()) : r).toList();
        return new Edition(context, dates, categories, changed);
    }

    public List<Registration> getRegistrationsByCategory(CategoryId categoryId) {
        category(categoryId);
        return registrations.stream().filter(r -> r.categoryId().equals(categoryId)).toList();
    }

    public void requireEligible(Team team) {
        Registration registration = registration(team.getId())
                .orElseThrow(() -> new IllegalArgumentException("Team not registered in edition: " + team.getId()));
        RegistrationEligibility.requireEligible(team, category(registration.categoryId()), registration.referenceDate());
    }

    /** Persistence restores relationships and timestamps without replaying enrollment or checking today's team. */
    public static Edition restore(EditionContext context, DateRange dates, List<Category> categories,
                                  List<Registration> registrations) {
        return new Edition(context, dates, categories, registrations);
    }

    public static Edition of(EditionContext context, DateRange dates) {
        return new Edition(context, dates);
    }

    public static Edition of(EditionId id, Tournament tournament, int editionNumber, String name,
                              LocalDate startDate, LocalDate endDate, List<Category> categories) {
        Edition edition = new Edition(
                new EditionContext(tournament, new EditionHeader(id, name, editionNumber)),
                new DateRange(startDate, endDate)
        );
        if (categories != null) {
            for (Category category : categories) edition = edition.addCategory(category);
        }
        return edition;
    }
}
