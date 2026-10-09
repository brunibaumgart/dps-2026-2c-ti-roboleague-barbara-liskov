package com.roboleague.api.scheduling;

import com.roboleague.scheduling.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

record RoundDto(String roundId, String challengeId, String editionId, String categoryId, int roundNumber,
                String name, String status, List<SlotDto> slots) {
    static RoundDto from(Round round) {
        return new RoundDto(round.getId().value(), round.getChallengeId().value(), round.getEditionId().value(),
                round.getCategoryId().value(), round.getRoundNumber(), round.getName(), round.getStatus().name(),
                round.getSlots().stream().sorted(Comparator.comparing(Slot::getStartTime)
                        .thenComparing(slot -> slot.getTrack().id().value()).thenComparing(slot -> slot.getSlotId().value()))
                        .map(SlotDto::from).toList());
    }

    record TrackDto(String id, String name, String surfaceType, boolean isActive) {
        static TrackDto from(Track track) { return new TrackDto(track.id().value(), track.name(), track.surfaceType(), track.isActive()); }
    }
    record JudgeDto(String id, String fullName, String specialty) {
        static JudgeDto from(Judge judge) { return new JudgeDto(judge.id().value(), judge.fullName(), judge.specialty()); }
    }
    record SlotDto(String slotId, String teamId, TrackDto track, List<JudgeDto> judges, LocalDateTime startTime,
                   LocalDateTime endTime, long intervalSeconds, String status) {
        static SlotDto from(Slot slot) {
            return new SlotDto(slot.getSlotId().value(), slot.getTeamId().value(), TrackDto.from(slot.getTrack()),
                    slot.getAssignedJudges().stream().map(JudgeDto::from).toList(), slot.getStartTime(), slot.getEndTime(),
                    slot.getTrackInterval().toSeconds(), slot.getStatus().name());
        }
    }
}
