package com.roboleague.api.challenge;

import com.roboleague.api.ErrorDto;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.usecase.AddChallengeCommand;
import com.roboleague.usecase.AddChallengeUseCase;
import com.roboleague.usecase.GetChallengeUseCase;
import com.roboleague.usecase.GetRulebookUseCase;
import com.roboleague.usecase.ListEditionChallengesUseCase;
import com.roboleague.usecase.Publication;
import com.roboleague.usecase.PublishRulebookUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Driving adapter for configuring challenges: add one to an edition, publish a new rulebook version, read it back.
 * A rejected rulebook is an expected outcome of the use case, so it maps to 422 here with every problem in details.
 */
@RestController
class ChallengeController {

    private final AddChallengeUseCase addChallenge;
    private final PublishRulebookUseCase publishRulebook;
    private final GetChallengeUseCase getChallenge;

    private final ListEditionChallengesUseCase listChallenges;
    private final GetRulebookUseCase getRulebook;

    ChallengeController(AddChallengeUseCase addChallenge, PublishRulebookUseCase publishRulebook,
                        GetChallengeUseCase getChallenge, ListEditionChallengesUseCase listChallenges, GetRulebookUseCase getRulebook) {
        this.listChallenges = listChallenges;
        this.getRulebook = getRulebook;
        this.addChallenge = addChallenge;
        this.publishRulebook = publishRulebook;
        this.getChallenge = getChallenge;
    }

    @GetMapping("/editions/{editionId}/challenges")
    List<ChallengeDto> list(@PathVariable String editionId) {
        return listChallenges.execute(EditionId.of(editionId)).stream().map(ChallengeDto::from).toList();
    }

    @PostMapping("/editions/{editionId}/challenges")
    ResponseEntity<?> add(@PathVariable String editionId, @RequestBody AddChallengeRequest request) {
        if (request.id() == null || request.name() == null || request.rulebook() == null) {
            throw new IllegalArgumentException("a challenge needs id, name and rulebook");
        }
        AddChallengeCommand command = new AddChallengeCommand(
                Challenge.draft(ChallengeId.of(request.id()), EditionId.of(editionId), request.name()),
                request.rulebook().toDefinition());
        return switch (addChallenge.execute(command)) {
            case Publication.Published<Challenge> published ->
                    ResponseEntity.status(HttpStatus.CREATED).body(ChallengeDto.from(published.value()));
            case Publication.Rejected<Challenge> rejected -> rulebookRejected(rejected.problems());
        };
    }

    @PostMapping("/challenges/{challengeId}/rulebook/versions")
    ResponseEntity<?> publish(@PathVariable String challengeId, @RequestBody RulebookBody rulebook) {
        return switch (publishRulebook.execute(ChallengeId.of(challengeId), rulebook.toDefinition())) {
            case Publication.Published<Rulebook> published ->
                    ResponseEntity.status(HttpStatus.CREATED).body(RulebookDto.from(published.value()));
            case Publication.Rejected<Rulebook> rejected -> rulebookRejected(rejected.problems());
        };
    }

    @GetMapping("/challenges/{challengeId}")
    ChallengeDto get(@PathVariable String challengeId) {
        return ChallengeDto.from(getChallenge.execute(ChallengeId.of(challengeId)));
    }

    @GetMapping("/challenges/{challengeId}/rulebook/versions/{version}")
    RulebookDto version(@PathVariable String challengeId, @PathVariable int version) {
        return RulebookDto.from(getRulebook.execute(ChallengeId.of(challengeId), new RulebookVersion(version)));
    }

    private static ResponseEntity<ErrorDto> rulebookRejected(List<String> problems) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorDto("Rulebook rejected", problems));
    }

    record AddChallengeRequest(String id, String name, RulebookBody rulebook) {
    }
}
