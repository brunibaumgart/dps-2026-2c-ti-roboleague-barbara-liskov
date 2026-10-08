package com.roboleague.api.edition;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.Tournament;
import com.roboleague.usecase.CreateEditionCommand;
import com.roboleague.usecase.CreateEditionUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Driving adapter for configuring the event: an edition of a tournament with its dates and categories.
 */
@RestController
class EditionController {

    private final CreateEditionUseCase createEdition;

    EditionController(CreateEditionUseCase createEdition) {
        this.createEdition = createEdition;
    }

    @PostMapping("/editions")
    @ResponseStatus(HttpStatus.CREATED)
    EditionDto create(@RequestBody EditionRequest request) {
        return EditionDto.from(createEdition.execute(request.toCommand()));
    }

    record EditionRequest(String id, String name, int editionNumber, TournamentBody tournament,
                          LocalDate startDate, LocalDate endDate, List<CategoryBody> categories) {

        CreateEditionCommand toCommand() {
            if (id == null || name == null || tournament == null || startDate == null || endDate == null) {
                throw new IllegalArgumentException("an edition needs id, name, tournament, startDate and endDate");
            }
            List<Category> parsed = new ArrayList<>();
            for (CategoryBody category : categories == null ? List.<CategoryBody>of() : categories) {
                if (category == null) {
                    throw new IllegalArgumentException("a category cannot be empty");
                }
                parsed.add(category.toCategory());
            }
            return new CreateEditionCommand(
                    new EditionContext(tournament.toTournament(), new EditionHeader(EditionId.of(id), name, editionNumber)),
                    new DateRange(startDate, endDate), parsed);
        }
    }

    record TournamentBody(String id, String name, String description, SeasonBody season) {

        Tournament toTournament() {
            if (id == null || name == null || season == null) {
                throw new IllegalArgumentException("a tournament needs id, name and season");
            }
            return Tournament.of(id, name, description == null ? "" : description, season.toSeason());
        }
    }

    record SeasonBody(String id, int year, String name) {

        Season toSeason() {
            if (id == null || name == null) {
                throw new IllegalArgumentException("a season needs id and name");
            }
            return new Season(id, year, name);
        }
    }

    record CategoryBody(String id, String name, int minMembers, int maxMembers, int minAge, int maxAge,
                        double maxWeightGrams) {

        Category toCategory() {
            if (id == null || name == null) {
                throw new IllegalArgumentException("a category needs id and name");
            }
            return Category.of(CategoryId.of(id), name, minMembers, maxMembers, minAge, maxAge, maxWeightGrams);
        }
    }

    record EditionDto(String id, String name, LocalDate startDate, LocalDate endDate, List<String> categories) {

        static EditionDto from(Edition edition) {
            return new EditionDto(edition.getId().value(), edition.getName(), edition.getStartDate(), edition.getEndDate(),
                    edition.getCategories().stream().map(category -> category.id().value()).toList());
        }
    }
}
