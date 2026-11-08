package com.roboleague.api.registration;

import com.roboleague.support.Clock;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import com.roboleague.usecase.QueryRegistrationsUseCase;
import com.roboleague.usecase.RegisterTeamUseCase;
import com.roboleague.usecase.UpdateRegistrationUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

@RestController
@RequestMapping("/editions/{editionId}/registrations")
class RegistrationController {
    private final RegisterTeamUseCase register;
    private final UpdateRegistrationUseCase update;
    private final QueryRegistrationsUseCase query;
    private final Clock clock;

    RegistrationController(RegisterTeamUseCase register, UpdateRegistrationUseCase update,
                           QueryRegistrationsUseCase query, Clock clock) {
        this.register = register;
        this.update = update;
        this.query = query;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationDto create(@PathVariable String editionId, @RequestBody RegistrationRequest request) {
        if ((request.team() == null) == (request.teamId() == null)) {
            throw new IllegalArgumentException("Provide exactly one of team or teamId");
        }
        EditionId edition = EditionId.of(editionId);
        CategoryId category = CategoryId.of(text(request.categoryId(), "categoryId"));
        var registration = request.team() == null
                ? register.execute(edition, category, TeamId.of(text(request.teamId(), "teamId")))
                : register.execute(edition, category, request.team().toTeam(clock));
        return RegistrationDto.from(query.get(edition, registration.teamId()));
    }

    @GetMapping
    List<RegistrationDto> list(@PathVariable String editionId) {
        return query.list(EditionId.of(editionId)).stream().map(RegistrationDto::from).toList();
    }

    @GetMapping("/{teamId}")
    RegistrationDto get(@PathVariable String editionId, @PathVariable String teamId) {
        return RegistrationDto.from(query.get(EditionId.of(editionId), TeamId.of(teamId)));
    }

    @PutMapping("/{teamId}")
    RegistrationDto update(@PathVariable String editionId, @PathVariable String teamId,
                           @RequestBody UpdateRegistrationRequest request) {
        return RegistrationDto.from(update.execute(EditionId.of(editionId), TeamId.of(teamId),
                CategoryId.of(text(request.categoryId(), "categoryId")), required(request.team(), "team").toTeam(clock)));
    }

    record RegistrationRequest(String categoryId, String teamId, TeamBody team) { }
    record UpdateRegistrationRequest(String categoryId, TeamBody team) { }
}
