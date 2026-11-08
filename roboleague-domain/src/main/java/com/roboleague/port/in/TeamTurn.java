package com.roboleague.port.in;

import com.roboleague.evaluation.Attempt;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;

import java.util.List;
import java.util.Objects;

/**
 * One turn of a team: its round, its slot and the attempts it ran there (none yet is valid), in the order they
 * were opened.
 */
public record TeamTurn(Round round, Slot slot, List<Attempt> attempts) {
    public TeamTurn {
        Objects.requireNonNull(round, "round cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts cannot be null"));
    }
}
