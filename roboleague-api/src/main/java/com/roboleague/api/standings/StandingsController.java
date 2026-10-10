package com.roboleague.api.standings;

import com.roboleague.api.ErrorDto;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsPublication;
import com.roboleague.ranking.StandingsVersion;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.usecase.PublishStandingsUseCase;
import com.roboleague.usecase.QueryStandingsUseCase;
import com.roboleague.usecase.RecalculateStandingsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.roboleague.api.RequestValues.text;

/**
 * Driving adapter for the standings of a challenge in a category. Every calculation is a new version; publishing
 * one is a sub-resource of it. A blocked publication is an expected outcome of the use case, so it maps to 409
 * here with every reason in details.
 */
@RestController
@RequestMapping("/challenges/{challengeId}/standings")
class StandingsController {

    private final QueryStandingsUseCase queryStandings;
    private final RecalculateStandingsUseCase recalculateStandings;
    private final PublishStandingsUseCase publishStandings;

    StandingsController(QueryStandingsUseCase queryStandings, RecalculateStandingsUseCase recalculateStandings,
                        PublishStandingsUseCase publishStandings) {
        this.queryStandings = queryStandings;
        this.recalculateStandings = recalculateStandings;
        this.publishStandings = publishStandings;
    }

    @GetMapping
    StandingsDto get(@PathVariable String challengeId, @RequestParam String categoryId) {
        return StandingsDto.from(id(challengeId, categoryId), queryStandings.execute(id(challengeId, categoryId)));
    }

    @GetMapping("/versions/{version}")
    StandingsVersionDto version(@PathVariable String challengeId, @PathVariable int version,
                                @RequestParam String categoryId) {
        StandingsId id = id(challengeId, categoryId);
        Standings standings = queryStandings.execute(id).standings()
                .orElseThrow(() -> new IllegalArgumentException("Standings not calculated yet: " + id));
        StandingsVersion found = standings.version(version)
                .orElseThrow(() -> new IllegalArgumentException("Standings " + id + " have no version " + version));
        return StandingsVersionDto.from(standings, found);
    }

    @PostMapping("/versions")
    ResponseEntity<StandingsVersionDto> recalculate(@PathVariable String challengeId, @RequestParam String categoryId) {
        Standings standings = recalculateStandings.execute(id(challengeId, categoryId));
        return ResponseEntity.status(HttpStatus.CREATED).body(StandingsVersionDto.from(standings, standings.latest()));
    }

    @PostMapping("/versions/{version}/publication")
    ResponseEntity<?> publish(@PathVariable String challengeId, @PathVariable int version,
                              @RequestParam String categoryId, @RequestBody PublicationRequest request) {
        AuditNote note = AuditNote.of(ActorId.of(text(request.publishedBy(), "publishedBy")),
                text(request.notes(), "notes"));
        StandingsId id = id(challengeId, categoryId);
        return switch (publishStandings.execute(id, version, note)) {
            case StandingsPublication.Published published ->
                    ResponseEntity.ok(StandingsDto.from(id, queryStandings.execute(id)));
            case StandingsPublication.Blocked blocked -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorDto("Publication blocked", blocked.reasons()));
        };
    }

    private static StandingsId id(String challengeId, String categoryId) {
        return new StandingsId(ChallengeId.of(challengeId), CategoryId.of(categoryId));
    }

    record PublicationRequest(String publishedBy, String notes) {
    }
}
