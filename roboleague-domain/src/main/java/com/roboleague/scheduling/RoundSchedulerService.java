package com.roboleague.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain service to schedule a round and create slots for teams across available tracks and judges.
 */
public class RoundSchedulerService {

    public Round scheduleRound(String roundId, String editionId, String categoryId, int roundNumber, String roundName,
                               List<String> teamIds, List<Track> tracks, List<Judge> judges,
                               LocalDateTime roundStart, Duration slotDuration, Duration intervalBetweenSlots) {
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamIds, "teamIds cannot be null");
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("At least one track must be available");
        }
        if (judges == null || judges.isEmpty()) {
            throw new IllegalArgumentException("At least one judge must be available");
        }

        Round round = new Round(roundId, editionId, categoryId, roundNumber, roundName);

        LocalDateTime currentStart = roundStart;
        int trackIndex = 0;
        int judgeIndex = 0;

        for (String teamId : teamIds) {
            Track track = tracks.get(trackIndex % tracks.size());
            LocalDateTime currentEnd = currentStart.plus(slotDuration);

            // Assign at least 1 judge, up to 2 if available
            List<Judge> slotJudges = new ArrayList<>();
            slotJudges.add(judges.get(judgeIndex % judges.size()));
            if (judges.size() > 1) {
                slotJudges.add(judges.get((judgeIndex + 1) % judges.size()));
            }

            Slot slot = new Slot(
                    UUID.randomUUID().toString(),
                    roundId,
                    teamId,
                    track,
                    currentStart,
                    currentEnd,
                    slotJudges
            );
            round.addSlot(slot);

            trackIndex++;
            judgeIndex++;
            currentStart = currentEnd.plus(intervalBetweenSlots);
        }

        return round;
    }
}
