package com.roboleague.api.graphql.view;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;

import java.util.List;

/**
 * GraphQL view of a slot (a turn of a team in a round). {@code team} and {@code turn} are resolved in batch, so the
 * view keeps {@code teamId} and {@code roundId}. {@code cancellation} is null unless the slot was cancelled.
 */
public record SlotView(String id, String roundId, String teamId, TrackView track, List<JudgeView> judges,
                       String startTime, String endTime, int intervalSeconds, String status,
                       CancellationView cancellation) {

    public static SlotView from(Round round, Slot slot) {
        return new SlotView(slot.getSlotId().value(), round.getId().value(), slot.getTeamId().value(),
                TrackView.from(slot.getTrack()), slot.getAssignedJudges().stream().map(JudgeView::from).toList(),
                Dates.iso(slot.getStartTime()), Dates.iso(slot.getEndTime()),
                Math.toIntExact(slot.getTrackInterval().toSeconds()), slot.getStatus().name(),
                slot.getCancellation().map(CancellationView::from).orElse(null));
    }
}
