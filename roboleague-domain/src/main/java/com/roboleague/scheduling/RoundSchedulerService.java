package com.roboleague.scheduling;

import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.TeamId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Selects the earliest free track/judge pair, with stable resource-order ties. */
public class RoundSchedulerService {
    private final IdGenerator ids;

    public RoundSchedulerService(IdGenerator ids) {
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
    }

    public Round scheduleRound(RoundScheduleRequest request, List<TeamId> teamIds) {
        return scheduleRound(request, teamIds, List.of());
    }

    public Round scheduleRound(RoundScheduleRequest request, List<TeamId> teamIds, List<Round> existingRounds) {
        Objects.requireNonNull(request, "request cannot be null");
        teamIds = List.copyOf(teamIds);
        if (teamIds.stream().distinct().count() != teamIds.size()) {
            throw new IllegalArgumentException("Team ids must be unique within a round");
        }
        List<Slot> occupied = new ArrayList<>(existingRounds.stream().flatMap(round -> round.getSlots().stream()).toList());
        Round round = Round.of(request.roundInfo());
        for (TeamId teamId : teamIds) {
            PlannedSlot best = null;
            for (Track track : request.resources().tracks()) {
                for (Judge judge : request.resources().judges()) {
                    LocalDateTime start = request.timing().roundStart();
                    PlannedSlot candidate;
                    while (true) {
                        candidate = new PlannedSlot(new SlotAssignment(track, List.of(judge)),
                                new TimeWindow(start, start.plus(request.timing().slotDuration())));
                        LocalDateTime next = start;
                        for (Slot slot : occupied) {
                            LocalDateTime blocked = SlotConflicts.blockedUntil(teamId, candidate.assignment(), candidate.window(),
                                    request.timing().intervalBetweenSlots(), slot).orElse(start);
                            if (blocked.isAfter(next)) { next = blocked; }
                        }
                        if (next.equals(start)) { break; }
                        start = next;
                    }
                    if (best == null || candidate.window().startTime().isBefore(best.window().startTime())) {
                        best = candidate;
                    }
                }
            }
            Slot slot = new Slot(new SlotIdentity(SlotId.of(ids.nextId()), round.getId(), teamId),
                    best.assignment(), best.window(), request.timing().intervalBetweenSlots());
            round = round.addSlot(slot);
            occupied.add(slot);
        }
        return round;
    }

    private record PlannedSlot(SlotAssignment assignment, TimeWindow window) { }
}
