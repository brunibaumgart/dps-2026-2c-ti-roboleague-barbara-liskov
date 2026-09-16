package com.roboleague.scheduling;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain service to schedule a round and create slots for teams across available tracks and judges.
 */
public class RoundSchedulerService {

    public Round scheduleRound(RoundScheduleRequest request, List<String> teamIds) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(teamIds, "teamIds cannot be null");

        Round round = new Round(request.roundInfo());
        List<Track> tracks = request.resources().tracks();
        List<Judge> judges = request.resources().judges();

        LocalDateTime currentStart = request.timing().roundStart();
        int trackIndex = 0;
        int judgeIndex = 0;

        for (String teamId : teamIds) {
            Track track = tracks.get(trackIndex % tracks.size());
            LocalDateTime currentEnd = currentStart.plus(request.timing().slotDuration());

            List<Judge> slotJudges = new ArrayList<>();
            slotJudges.add(judges.get(judgeIndex % judges.size()));
            if (judges.size() > 1) {
                slotJudges.add(judges.get((judgeIndex + 1) % judges.size()));
            }

            SlotIdentity identity = new SlotIdentity(UUID.randomUUID().toString(), round.getId(), teamId);
            SlotAssignment assignment = new SlotAssignment(track, slotJudges);
            TimeWindow timeWindow = new TimeWindow(currentStart, currentEnd);

            Slot slot = new Slot(identity, assignment, timeWindow);
            round.addSlot(slot);

            trackIndex++;
            judgeIndex++;
            currentStart = currentEnd.plus(request.timing().intervalBetweenSlots());
        }

        return round;
    }
}
