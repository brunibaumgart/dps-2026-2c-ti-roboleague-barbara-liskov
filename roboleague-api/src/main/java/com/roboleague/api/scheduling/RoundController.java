package com.roboleague.api.scheduling;

import com.roboleague.scheduling.*;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.usecase.GetChallengeUseCase;
import com.roboleague.usecase.QueryRoundsUseCase;
import com.roboleague.usecase.ScheduleRoundCommand;
import com.roboleague.usecase.ScheduleRoundUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.roboleague.api.RequestValues.required;
import static com.roboleague.api.RequestValues.text;

@RestController
class RoundController {
    private final ScheduleRoundUseCase schedule;
    private final QueryRoundsUseCase query;
    private final GetChallengeUseCase challenges;

    RoundController(ScheduleRoundUseCase schedule, QueryRoundsUseCase query, GetChallengeUseCase challenges) {
        this.schedule = schedule;
        this.query = query;
        this.challenges = challenges;
    }

    @PostMapping("/challenges/{challengeId}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    RoundDto create(@PathVariable String challengeId, @RequestBody RoundRequest request) {
        var challenge = challenges.execute(ChallengeId.of(challengeId));
        ScheduleRoundCommand command = ScheduleRoundCommand.of(challenge.getId(), challenge.getEditionId(),
                CategoryId.of(text(request.categoryId(), "categoryId")), required(request.roundNumber(), "roundNumber"),
                text(request.roundName(), "roundName"), required(request.tracks(), "tracks").stream()
                        .map(track -> required(track, "tracks[]").toTrack()).toList(),
                required(request.judges(), "judges").stream().map(judge -> required(judge, "judges[]").toJudge()).toList(),
                required(request.startTime(), "startTime"), Duration.ofSeconds(required(request.slotDurationSeconds(), "slotDurationSeconds")),
                Duration.ofSeconds(required(request.intervalSeconds(), "intervalSeconds")));
        return RoundDto.from(schedule.execute(command));
    }

    @GetMapping("/challenges/{challengeId}/rounds")
    List<RoundDto> list(@PathVariable String challengeId, @RequestParam(required = false) String categoryId) {
        return query.list(ChallengeId.of(challengeId), Optional.ofNullable(categoryId).map(CategoryId::of))
                .stream().map(RoundDto::from).toList();
    }

    @GetMapping("/rounds/{roundId}")
    RoundDto get(@PathVariable String roundId) { return RoundDto.from(query.get(RoundId.of(roundId))); }

    record RoundRequest(String categoryId, Integer roundNumber, String roundName, LocalDateTime startTime,
                        Long slotDurationSeconds, Long intervalSeconds, List<TrackBody> tracks, List<JudgeBody> judges) { }
    record TrackBody(String id, String name, String surfaceType, Boolean isActive) {
        Track toTrack() {
            return Track.of(TrackInfo.of(TrackId.of(text(id, "track.id")), text(name, "track.name"),
                    text(surfaceType, "track.surfaceType")), required(isActive, "track.isActive"));
        }
    }
    record JudgeBody(String id, String fullName, String specialty) {
        Judge toJudge() { return Judge.of(JudgeId.of(text(id, "judge.id")), text(fullName, "judge.fullName"), text(specialty, "judge.specialty")); }
    }
}
