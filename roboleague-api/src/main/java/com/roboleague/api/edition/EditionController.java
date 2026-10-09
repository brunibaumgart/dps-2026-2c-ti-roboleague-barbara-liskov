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
import com.roboleague.usecase.QueryEditionsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final QueryEditionsUseCase query;

    EditionController(CreateEditionUseCase createEdition, QueryEditionsUseCase query) {
        this.query = query;
        this.createEdition = createEdition;
    }

    @PostMapping("/editions")
    @ResponseStatus(HttpStatus.CREATED)
    EditionDto create(@RequestBody EditionRequest request) {
        return EditionDto.from(createEdition.execute(request.toCommand()));
    }

    @GetMapping("/editions")
    List<EditionDto> list() { return query.list().stream().map(EditionDto::from).toList(); }

    @GetMapping("/editions/{editionId}")
    EditionDto get(@PathVariable String editionId) { return EditionDto.from(query.get(EditionId.of(editionId))); }

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

    record CategoryBody(String id, String name, Integer minMembers, Integer maxMembers, Integer minAge, Integer maxAge,
                        Double maxWeightGrams, Double maxLengthMm, Double maxWidthMm, Double maxHeightMm) {

        Category toCategory() {
            if (id == null || name == null) {
                throw new IllegalArgumentException("a category needs id and name");
            }
            return Category.of(CategoryId.of(id), name,
                    com.roboleague.api.RequestValues.required(minMembers, "category.minMembers"),
                    com.roboleague.api.RequestValues.required(maxMembers, "category.maxMembers"),
                    com.roboleague.api.RequestValues.required(minAge, "category.minAge"),
                    maxAge,
                    com.roboleague.api.RequestValues.required(maxWeightGrams, "category.maxWeightGrams"),
                    maxLengthMm == null ? 1000.0 : maxLengthMm,
                    maxWidthMm == null ? 1000.0 : maxWidthMm,
                    maxHeightMm == null ? 1000.0 : maxHeightMm);
        }
    }

    record EditionDto(String id, String name, LocalDate startDate, LocalDate endDate, List<String> categories, List<CategoryDto> categoryDetails) {

        static EditionDto from(Edition edition) {
            return new EditionDto(edition.getId().value(), edition.getName(), edition.getStartDate(), edition.getEndDate(),
                    edition.getCategories().stream().map(category -> category.id().value()).toList(),
                    edition.getCategories().stream().map(CategoryDto::from).toList());
        }
    }
    record CategoryDto(String id, String name, int minMembers, int maxMembers, int minAge, Integer maxAge,
                       double maxWeightGrams, double maxLengthMm, double maxWidthMm, double maxHeightMm) {
        static CategoryDto from(Category category) {
            return new CategoryDto(category.id().value(), category.name(), category.minTeamMembers(), category.maxTeamMembers(),
                    category.minAge(), category.maxAge(), category.maxRobotWeightGrams(), category.maxRobotLengthMm(),
                    category.maxRobotWidthMm(), category.maxRobotHeightMm());
        }
    }
}
