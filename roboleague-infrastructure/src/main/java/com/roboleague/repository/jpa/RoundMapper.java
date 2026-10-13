package com.roboleague.repository.jpa;

import com.roboleague.repository.jpa.RoundJpaEntity.JudgeJson;
import com.roboleague.repository.jpa.RoundJpaEntity.SlotJson;
import com.roboleague.repository.jpa.RoundJpaEntity.TrackJson;
import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.JudgeProfile;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Round.RoundStatus;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundInfo;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.Slot.SlotStatus;
import com.roboleague.scheduling.SlotAssignment;
import com.roboleague.scheduling.SlotId;
import com.roboleague.scheduling.SlotIdentity;
import com.roboleague.scheduling.TimeWindow;
import com.roboleague.scheduling.Track;
import com.roboleague.scheduling.TrackId;
import com.roboleague.scheduling.TrackInfo;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Anti-corruption layer between the rounds table and the {@link Round} aggregate. Reading restores the round and
 * each slot in its stored state, without replaying the schedule or the transitions.
 */
final class RoundMapper {

    private RoundMapper() {
    }

    static RoundJpaEntity toEntity(Round round) {
        RoundJpaEntity entity = new RoundJpaEntity();
        entity.id = round.getId().value();
        entity.name = round.getName();
        entity.challengeId = round.getChallengeId().value();
        entity.editionId = round.getEditionId().value();
        entity.categoryId = round.getCategoryId().value();
        entity.roundNumber = round.getRoundNumber();
        entity.status = round.getStatus().name();
        entity.slots = round.getSlots().stream().map(RoundMapper::toJson).toList();
        return entity;
    }

    static Round toDomain(RoundJpaEntity entity) {
        RoundId id = RoundId.of(entity.id);
        RoundInfo info = new RoundInfo(id, entity.name, new RoundScope(ChallengeId.of(entity.challengeId),
                EditionId.of(entity.editionId), CategoryId.of(entity.categoryId), entity.roundNumber));
        return Round.restore(info, RoundStatus.valueOf(entity.status),
                entity.slots.stream().map(json -> toSlot(id, json)).toList());
    }

    private static SlotJson toJson(Slot slot) {
        Track track = slot.getTrack();
        return new SlotJson(slot.getSlotId().value(), slot.getTeamId().value(), slot.getStatus().name(),
                slot.getStartTime().toString(), slot.getEndTime().toString(), slot.getTrackInterval().toString(),
                new TrackJson(track.id().value(), track.name(), track.surfaceType(), track.isActive()),
                slot.getAssignedJudges().stream()
                        .map(judge -> new JudgeJson(judge.id().value(), judge.fullName(), judge.specialty(),
                                judge.certificationLevel()))
                        .toList());
    }

    private static Slot toSlot(RoundId roundId, SlotJson json) {
        TrackJson track = json.track();
        return Slot.restore(new SlotIdentity(SlotId.of(json.slotId()), roundId, TeamId.of(json.teamId())),
                new SlotAssignment(new Track(new TrackInfo(TrackId.of(track.id()), track.name(), track.surfaceType()),
                        track.active()),
                        json.judges().stream()
                                .map(judge -> new Judge(new JudgeProfile(JudgeId.of(judge.id()), judge.fullName()),
                                        judge.specialty(), judge.certificationLevel()))
                                .toList()),
                new TimeWindow(LocalDateTime.parse(json.startTime()), LocalDateTime.parse(json.endTime())),
                Duration.parse(json.trackInterval()), SlotStatus.valueOf(json.status()));
    }
}
