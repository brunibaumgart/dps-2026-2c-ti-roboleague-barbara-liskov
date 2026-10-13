package com.roboleague.repository.jpa;

import com.roboleague.repository.jpa.EditionJpaEntity.CategoryJson;
import com.roboleague.repository.jpa.EditionJpaEntity.RegistrationJson;
import com.roboleague.repository.jpa.EditionJpaEntity.TournamentJson;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.Tournament;
import com.roboleague.tournament.TournamentInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Anti-corruption layer between the editions table and the {@link Edition} aggregate. Reading restores the stored
 * categories and registrations, with their reference dates and registration times, without replaying enrollment.
 */
final class EditionMapper {

    private EditionMapper() {
    }

    static EditionJpaEntity toEntity(Edition edition) {
        EditionJpaEntity entity = new EditionJpaEntity();
        entity.id = edition.getId().value();
        entity.name = edition.getName();
        entity.editionNumber = edition.getEditionNumber();
        entity.startDate = edition.getStartDate();
        entity.endDate = edition.getEndDate();
        Tournament tournament = edition.getTournament();
        Season season = tournament.season();
        entity.tournament = new TournamentJson(tournament.id(), tournament.name(), tournament.description(),
                season.id(), season.year(), season.name());
        entity.categories = edition.getCategories().stream().map(EditionMapper::toJson).toList();
        entity.registrations = edition.getRegistrations().stream()
                .map(registration -> new RegistrationJson(registration.teamId().value(),
                        registration.categoryId().value(), registration.referenceDate().toString(),
                        registration.registeredAt().toString()))
                .toList();
        return entity;
    }

    static Edition toDomain(EditionJpaEntity entity) {
        EditionId id = EditionId.of(entity.id);
        TournamentJson tournament = entity.tournament;
        EditionContext context = new EditionContext(
                new Tournament(new TournamentInfo(tournament.id(), tournament.name(), tournament.description()),
                        new Season(tournament.seasonId(), tournament.seasonYear(), tournament.seasonName())),
                new EditionHeader(id, entity.name, entity.editionNumber));
        return Edition.restore(context, new DateRange(entity.startDate, entity.endDate),
                entity.categories.stream().map(EditionMapper::toCategory).toList(),
                entity.registrations.stream()
                        .map(json -> new Registration(TeamId.of(json.teamId()), id, CategoryId.of(json.categoryId()),
                                LocalDate.parse(json.referenceDate()), LocalDateTime.parse(json.registeredAt())))
                        .toList());
    }

    private static CategoryJson toJson(Category category) {
        return new CategoryJson(category.id().value(), category.name(), category.minTeamMembers(),
                category.maxTeamMembers(), category.minAge(), category.maxAge(), category.maxRobotWeightGrams(),
                category.maxRobotLengthMm(), category.maxRobotWidthMm(), category.maxRobotHeightMm());
    }

    private static Category toCategory(CategoryJson json) {
        return Category.of(CategoryId.of(json.id()), json.name(), json.minMembers(), json.maxMembers(), json.minAge(),
                json.maxAge(), json.maxWeightGrams(), json.maxLengthMm(), json.maxWidthMm(), json.maxHeightMm());
    }
}
